# SmartHire full QA run — uses curl.exe, subprocess; no secrets logged.
import json
import os
import re
import subprocess
import sys
from datetime import datetime, timezone

ROOT = os.path.dirname(os.path.abspath(__file__))
BACKEND = os.path.join(ROOT, "smarthire-backend")
AI_SVC = os.path.join(ROOT, "smarthire-ai-service")
LOG = os.path.join(ROOT, "qa-run.log")

CURL = ["curl.exe", "--max-time", "15", "-sS"]


def run_curl(args, extra_headers=None, data=None, method=None):
    cmd = list(CURL)
    if method:
        cmd.extend(["-X", method])
    if extra_headers:
        for h in extra_headers:
            cmd.extend(["-H", h])
    if data is not None:
        cmd.extend(["-d", data])
    cmd.extend(args)
    try:
        p = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
        return p.returncode, (p.stdout or "") + (p.stderr or "")
    except subprocess.TimeoutExpired:
        return -1, "TIMEOUT"


def run_shell(cmd, cwd=None, timeout=600):
    try:
        p = subprocess.run(
            cmd,
            shell=True,
            cwd=cwd or ROOT,
            capture_output=True,
            text=True,
            timeout=timeout,
        )
        return p.returncode, (p.stdout or "") + (p.stderr or "")
    except subprocess.TimeoutExpired:
        return -1, "TIMEOUT"


def curl_with_code(url, method="GET", headers=None, data=None):
    """Returns body, http_code using -w."""
    cmd = list(CURL) + ["-w", "\nHTTP:%{http_code}"]
    if method and method != "GET":
        cmd.extend(["-X", method])
    if headers:
        for h in headers:
            cmd.extend(["-H", h])
    if data is not None:
        cmd.extend(["-d", data])
    cmd.append(url)
    try:
        p = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
        out = (p.stdout or "") + (p.stderr or "")
    except subprocess.TimeoutExpired:
        return "", "000"
    m = re.search(r"HTTP:(\d+)$", out, re.MULTILINE)
    code = m.group(1) if m else "000"
    body = out[: m.start()].rstrip() if m else out
    return body, code


issues = []
warnings = []


def add_issue(msg, fix):
    issues.append((msg, fix))


def add_warn(msg):
    warnings.append(msg)


lines_out = []


def pr(msg=""):
    lines_out.append(msg)
    print(msg)


def main():
    pr("=" * 60)
    pr("SMARTHIRE QA RUNNER (Windows: curl.exe)")
    pr("Started: " + datetime.now(timezone.utc).astimezone().strftime("%Y-%m-%d %H:%M:%S %Z"))
    pr("=" * 60)

    # ----- STEP 1 -----
    svc_pass = 0
    spring_up = False
    py_up = False
    pg_health = "UNHEALTHY"
    kafka_health = "UNHEALTHY"
    swagger_java = "FAIL"
    swagger_py = "FAIL"

    body, _ = curl_with_code("http://localhost:8080/actuator/health")
    if '"status":"UP"' in body.replace(" ", "") or '"status": "UP"' in body:
        svc_pass += 1
        spring_up = True
    else:
        add_issue("Spring actuator not UP", "Start backend; check actuator and dependencies")

    body, _ = curl_with_code("http://localhost:8000/health")
    bl = body.lower()
    if "status" in bl and ("ok" in bl or "healthy" in bl):
        svc_pass += 1
        py_up = True
    else:
        add_issue("Python /health not ok", "Start ai-service on 8000")

    _, code = curl_with_code("http://localhost:8080/swagger-ui/index.html")
    if code == "200":
        svc_pass += 1
        swagger_java = "OK"
    else:
        add_issue(f"Swagger UI HTTP {code}", "Check SecurityConfig permitAll for swagger-ui")

    _, code = curl_with_code("http://localhost:8000/docs")
    if code == "200":
        svc_pass += 1
        swagger_py = "OK"
    else:
        add_issue(f"FastAPI /docs HTTP {code}", "Start ai-service")

    rc, dcout = run_shell("docker compose ps", cwd=ROOT, timeout=60)
    docker_ok = False
    if rc == 0 and dcout.strip():
        bad = re.search(r"\b(exited|restarting|dead)\b", dcout, re.I)
        if not bad:
            docker_ok = True
            svc_pass += 1
        else:
            add_issue("Docker compose has unhealthy containers", "docker compose ps; fix failing service")
    else:
        add_issue("docker compose ps failed", "Ensure Docker Desktop is running from repo root")

    # infer postgres/kafka from compose ps
    if "postgres" in dcout.lower() and ("healthy" in dcout.lower() or "running" in dcout.lower()):
        if re.search(r"postgres.*(healthy|running)", dcout, re.I | re.S):
            pg_health = "HEALTHY"
    if "kafka" in dcout.lower():
        if re.search(r"kafka.*(healthy|running)", dcout, re.I | re.S):
            kafka_health = "HEALTHY"

    pr("")
    pr(f"SERVICE CHECK: {svc_pass}/5 passed")

    # ----- STEP 2 AUTH (exact user payloads) -----
    auth_pass = 0
    token = None

    qa_user = "qaspec_" + str(int(datetime.now().timestamp()))[-6:]
    qa_email = f"{qa_user}@smarthire.com"

    reg_body, reg_code = curl_with_code(
        "http://localhost:8080/api/auth/register",
        "POST",
        ["Content-Type: application/json"],
        json.dumps({"username": qa_user, "email": qa_email, "password": "Test@1234"}),
    )
    if reg_code in ("200", "201") and "token" in reg_body:
        auth_pass += 1
        try:
            token = json.loads(reg_body.split("\n")[0])["token"]
        except Exception:
            pass
    else:
        pr(f"A1 (spec) FAIL HTTP {reg_code} body: {reg_body[:500]}")

    login_body, login_code = curl_with_code(
        "http://localhost:8080/api/auth/login",
        "POST",
        ["Content-Type: application/json"],
        json.dumps({"username": qa_user, "password": "Test@1234"}),
    )
    if login_code == "200" and "token" in login_body:
        auth_pass += 1
        try:
            token = json.loads(login_body.split("\n")[0])["token"]
        except Exception:
            pass
    else:
        pr(f"A2 (spec) FAIL HTTP {login_code} body: {login_body[:500]}")

    bad_login, bad_code = curl_with_code(
        "http://localhost:8080/api/auth/login",
        "POST",
        ["Content-Type: application/json"],
        json.dumps({"username": qa_user, "password": "wrongpass"}),
    )
    if bad_code in ("401", "403"):
        auth_pass += 1
    else:
        pr(f"A3 (spec) expected 401/403 got {bad_code}: {bad_login[:300]}")

    nop, nop_code = curl_with_code("http://localhost:8080/api/candidates")
    if nop_code in ("401", "403"):
        auth_pass += 1
    else:
        pr(f"A4 FAIL expected 401/403 got {nop_code}")

    pr(f"AUTH TESTS: {auth_pass}/4 passed")

    # Canonical auth for downstream (username + register shape per DTO)
    if not token:
        uname = "qatester_" + str(int(datetime.now().timestamp()))[-6:]
        reg_ok, rc_reg = curl_with_code(
            "http://localhost:8080/api/auth/register",
            "POST",
            ["Content-Type: application/json"],
            json.dumps(
                {
                    "username": uname,
                    "email": f"{uname}@smarthire.com",
                    "password": "Test@1234",
                }
            ),
        )
        if rc_reg not in ("200", "201") or "token" not in reg_ok:
            pr(f"Canonical register FAIL HTTP {rc_reg}: {reg_ok[:400]}")
        else:
            try:
                token = json.loads(reg_ok.split("\n")[0])["token"]
            except Exception:
                pass
        if not token:
            lb, lc = curl_with_code(
                "http://localhost:8080/api/auth/login",
                "POST",
                ["Content-Type: application/json"],
                json.dumps({"username": uname, "password": "Test@1234"}),
            )
            if lc == "200" and "token" in lb:
                token = json.loads(lb.split("\n")[0])["token"]

    if token:
        pr("(Downstream CRUD/screening uses canonical username/password API token.)")
    else:
        add_issue("No JWT for CRUD tests", "Fix register/login; ensure DB reachable")

    # ----- STEP 3 CRUD -----
    crud_pass = 0
    job_id = None
    cand_id = None
    headers_tok = lambda: [
        "Authorization: Bearer " + token,
        "Content-Type: application/json",
    ]

    if token:
        jb, jc = curl_with_code(
            "http://localhost:8080/api/jobs",
            "POST",
            headers_tok(),
            json.dumps(
                {
                    "title": "Senior Java Developer",
                    "description": "We need a Java expert with microservices experience",
                    "requirements": "Java, Spring Boot, Kafka, Docker, AWS",
                }
            ),
        )
        if jc == "201" and '"id"' in jb.replace(" ", ""):
            crud_pass += 1
            try:
                job_id = str(json.loads(jb.split("\n")[0])["id"])
            except Exception:
                m = re.search(r'"id"\s*:\s*(\d+)', jb)
                if m:
                    job_id = m.group(1)
        else:
            pr(f"B1 FAIL HTTP {jc}: {jb[:500]}")

        jb2, jc2 = curl_with_code("http://localhost:8080/api/jobs", "GET", headers_tok())
        if jc2 == "200" and len(jb2) > 2:
            crud_pass += 1
        else:
            pr(f"B2 FAIL HTTP {jc2}")

        cb, cc = curl_with_code(
            "http://localhost:8080/api/candidates",
            "POST",
            headers_tok(),
            json.dumps(
                {
                    "name": "Test Candidate",
                    "email": f"candidate_{qa_user}@test.com",
                    "resumeText": "Java developer with 3 years experience in Spring Boot, Microservices, Kafka, Docker, REST APIs, PostgreSQL and AWS deployment",
                }
            ),
        )
        if cc == "201" and "PENDING" in cb.upper():
            crud_pass += 1
            try:
                cand_id = str(json.loads(cb.split("\n")[0])["id"])
            except Exception:
                m = re.search(r'"id"\s*:\s*(\d+)', cb)
                if m:
                    cand_id = m.group(1)
        else:
            pr(f"B3 FAIL HTTP {cc}: {cb[:500]}")

        if cand_id:
            g1, g1c = curl_with_code(
                f"http://localhost:8080/api/candidates/{cand_id}",
                "GET",
                headers_tok(),
            )
            if g1c == "200" and cand_id in g1:
                crud_pass += 1
            else:
                pr(f"B4 FAIL HTTP {g1c}")

        g2, g2c = curl_with_code(
            "http://localhost:8080/api/candidates/99999",
            "GET",
            headers_tok(),
        )
        if g2c == "404":
            crud_pass += 1
        else:
            pr(f"B5 FAIL expected 404 got {g2c}")

    pr(f"CRUD TESTS: {crud_pass}/5 passed")

    # ----- STEP 4 AI -----
    ai_pass = 0
    ai_score_print = None
    c1b, c1c = curl_with_code(
        "http://localhost:8000/api/ai/screen",
        "POST",
        ["Content-Type: application/json"],
        json.dumps(
            {
                "resume_text": "Java developer with 3 years Spring Boot, Kafka, Microservices, Docker, AWS, PostgreSQL experience",
                "job_description": "Senior Java Developer needed with Spring Boot and Kafka expertise",
            }
        ),
    )
    if c1c == "200":
        try:
            j = json.loads(c1b.split("\n")[0])
            sc = j.get("score")
            ok = (
                isinstance(sc, int)
                and 0 <= sc <= 100
                and isinstance(j.get("strengths"), list)
                and len(j.get("strengths", [])) > 0
                and isinstance(j.get("weaknesses"), list)
                and isinstance(j.get("recommendation"), str)
                and len(j.get("recommendation", "")) > 0
            )
            if ok:
                ai_pass += 1
                ai_score_print = sc
                pr(f"C1 score={sc}")
            else:
                pr(f"C1 FAIL schema: {c1b[:600]}")
        except Exception:
            pr(f"C1 FAIL parse: {c1b[:600]}")
    else:
        pr(f"C1 FAIL HTTP {c1c}: {c1b[:600]}")

    c2b, c2c = curl_with_code(
        "http://localhost:8000/api/ai/screen",
        "POST",
        ["Content-Type: application/json"],
        '{"resume_text":"some resume"}',
    )
    if c2c == "422":
        ai_pass += 1
    else:
        pr(f"C2 expected 422 got {c2c}: {c2b[:300]}")

    if token and job_id and cand_id:
        c3b, c3c = curl_with_code(
            f"http://localhost:8080/api/candidates/{cand_id}/screen/{job_id}",
            "POST",
            headers_tok(),
        )
        if c3c == "200":
            ai_pass += 1
        else:
            pr(f"C3 FAIL HTTP {c3c}: {c3b[:400]}")
        import time

        time.sleep(5)
        c4b, c4c = curl_with_code(
            f"http://localhost:8080/api/candidates/{cand_id}",
            "GET",
            headers_tok(),
        )
        if c4c == "200":
            try:
                cj = json.loads(c4b.split("\n")[0])
                st = str(cj.get("status", "")).upper()
                sc = cj.get("aiScore")
                rec = cj.get("aiRecommendation")
                good = (
                    st == "SCREENED"
                    and sc is not None
                    and rec is not None
                    and str(rec).strip() != ""
                )
                if good:
                    ai_pass += 1
                    rs = str(rec)[:100]
                    pr(f"C4 AI Score={sc}, Recommendation: {rs}")
                else:
                    pr(f"C4 FAIL body: {c4b[:800]}")
                    add_issue("Candidate not SCREENED after flow", "Check AIScreeningService, Groq key, Kafka consumer")
            except Exception:
                pr(f"C4 FAIL parse: {c4b[:600]}")
        else:
            pr(f"C4 FAIL HTTP {c4c}")
    else:
        pr("C3/C4 skipped (missing token/job/candidate)")

    pr(f"AI SCREENING TESTS: {ai_pass}/4 passed")

    # ----- STEP 5 KAFKA -----
    k_pass = 0
    k_msg_preview = ""
    rc, kt = run_shell(
        'docker compose exec -T kafka kafka-topics --bootstrap-server localhost:9092 --list 2>&1',
        cwd=ROOT,
        timeout=90,
    )
    if "resume-screening" in kt:
        k_pass += 1
    else:
        pr(f"D1 topic list (fail excerpt): {kt[:400]}")
        add_issue("Topic resume-screening missing", "Ensure backend creates topic and Kafka healthy")

    rc, kcons = run_shell(
        "docker compose exec -T kafka kafka-console-consumer --bootstrap-server localhost:9092 "
        "--topic resume-screening --from-beginning --max-messages 1 --timeout-ms 8000 2>&1",
        cwd=ROOT,
        timeout=120,
    )
    if kcons.strip() and "ERROR" not in kcons.upper() and "timeout" not in kcons.lower():
        k_pass += 1
        k_msg_preview = kcons.strip()[:200]
        pr(f"D2 message preview: {k_msg_preview}")
    elif "resume-screening" in kt:
        pr(f"D2 consumer output: {kcons[:500]}")
        add_warn("Kafka consumer may have timed out with no messages yet")

    rc, kgr = run_shell(
        "docker compose exec -T kafka kafka-consumer-groups --bootstrap-server localhost:9092 --list 2>&1",
        cwd=ROOT,
        timeout=90,
    )
    lines = [x for x in kgr.splitlines() if x.strip()]
    if len(lines) >= 1 and "error" not in kgr.lower():
        k_pass += 1
    else:
        pr(f"D3 groups: {kgr[:400]}")

    pr(f"KAFKA TESTS: {k_pass}/3 passed")

    # ----- STEP 6 JAVA -----
    pr("")
    pr("Running mvn test (smarthire-backend)...")
    rc, mout = run_shell("mvn test -B 2>&1", cwd=BACKEND, timeout=900)
    j_tr = re.search(r"Tests run:\s*(\d+).*Failures:\s*(\d+).*Errors:\s*(\d+)", mout, re.S)
    j_tests = j_tr.group(1) if j_tr else "0"
    j_fail = j_tr.group(2) if j_tr else "1"
    j_err = j_tr.group(3) if j_tr else "0"
    java_ok = rc == 0 and "BUILD SUCCESS" in mout and j_fail == "0" and j_err == "0"
    if not java_ok:
        add_issue("Java unit tests failed", "Inspect mvn test output; fix failing tests")
        for line in mout.splitlines():
            if "FAILURE" in line or "<<< FAILURE" in line or "ERROR" in line:
                if "Tests run" not in line:
                    pr("  " + line[:200])
    pr(f"JAVA UNIT TESTS: {'PASSED' if java_ok else 'FAILED'} ({j_tests} tests, failures={j_fail}, errors={j_err})")

    # ----- STEP 7 PYTHON -----
    pr("")
    pr("Running pytest (smarthire-ai-service)...")
    rc, pout = run_shell("docker compose exec -T ai-service python -m pytest tests/ -v --tb=short 2>&1", cwd=ROOT, timeout=600)
    pm = re.search(r"(\d+)\s+passed", pout)
    pfail = re.search(r"(\d+)\s+failed", pout)
    p_pass_n = pm.group(1) if pm else "0"
    p_fail_n = pfail.group(1) if pfail else "0"
    py_ok = rc == 0 and p_fail_n == "0"
    if not py_ok:
        add_issue("Python tests failed", "pytest -v in smarthire-ai-service")
        for line in pout.splitlines():
            if "FAILED" in line:
                pr(line[:220])
    pr(f"PYTHON UNIT TESTS: {'PASSED' if py_ok else 'FAILED'} ({p_pass_n} passed, {p_fail_n} failed)")

    # ----- STEP 8 COVERAGE -----
    pr("")
    pr("Running mvn verify for Java coverage snippet...")
    _, vout = run_shell("mvn verify -B 2>&1", cwd=BACKEND, timeout=900)
    java_cov = "N/A"
    for line in vout.splitlines():
        if "jacoco" in line.lower() or "%" in line or "missed" in line.lower():
            pr(line[:200])
        m = re.search(r"(\d+)\s*%", line)
        if m and "line" in line.lower():
            java_cov = m.group(1) + "%"
    if java_cov == "N/A":
        m2 = re.search(r"Total.*?(\d+)%", vout.replace("\n", " "))
        if m2:
            java_cov = m2.group(1) + "%"

    pr("Python coverage (tail)...")
    _, covp = run_shell(
        "docker compose exec -T ai-service python -m pytest --cov=. --cov-report=term-missing tests/ 2>&1",
        cwd=ROOT,
        timeout=600,
    )
    tail = "\n".join(covp.splitlines()[-25:])
    pr(tail)
    py_cov = "N/A"
    cm = re.search(r"TOTAL\s+.*?\s+(\d+)%", tail)
    if cm:
        py_cov = cm.group(1) + "%"
    try:
        jn = float(java_cov.replace("%", "")) if java_cov.endswith("%") else 0
        if jn and jn < 80:
            add_warn(f"Java line coverage {java_cov} below 80%")
    except ValueError:
        pass
    try:
        pn = float(py_cov.replace("%", "")) if py_cov.endswith("%") else 0
        if pn and pn < 75:
            add_warn(f"Python coverage {py_cov} below 75%")
    except ValueError:
        pass

    pr(f"COVERAGE: Java {java_cov} / Python {py_cov}")

    # ----- STEP 9 -----
    pr("")
    _, dst = run_shell("docker stats --no-stream 2>&1", timeout=60)
    pr(dst[:2000])
    mem_high = False
    cpu_high = False
    for row in dst.splitlines()[1:]:
        parts = row.split("\t")
        if len(parts) >= 3:
            mem = parts[1]
            cpu = parts[2].replace("%", "")
            if "GiB" in mem or "Gib" in mem:
                mem_high = True
            try:
                if "MiB" in mem or "Mib" in mem:
                    num = float(re.search(r"([\d.]+)", mem).group(1))
                    if num > 1024:
                        mem_high = True
            except Exception:
                pass
            try:
                if float(cpu) > 80:
                    cpu_high = True
            except ValueError:
                pass

    _, lb = run_shell("docker compose logs --tail=5 backend 2>&1", cwd=ROOT, timeout=60)
    _, la = run_shell("docker compose logs --tail=5 ai-service 2>&1", cwd=ROOT, timeout=60)
    log_err = False
    for chunk, name in ((lb, "backend"), (la, "ai-service")):
        if re.search(r"\b(ERROR|EXCEPTION)\b", chunk, re.I):
            log_err = True
            add_warn(f"{name} logs show ERROR/EXCEPTION in last 5 lines")

    ch = "WARNINGS FOUND" if (mem_high or cpu_high or log_err) else "OK"
    if mem_high:
        add_warn("Container memory over threshold")
    if cpu_high:
        add_warn("Container CPU over 80%")
    pr(f"CONTAINER HEALTH: {ch}")

    # ----- STEP 10 REPORT CARD -----
    total_api = auth_pass + crud_pass + ai_pass + k_pass
    total_api_max = 16  # auth 4 + crud 5 + ai 4 + kafka 3 (per report card)
    java_fail_total = int(j_fail or 0) + int(j_err or 0) if j_fail.isdigit() else 1
    if not java_ok:
        java_fail_total = max(int(j_fail or 1), 1)

    unit_pass = int(p_pass_n) if p_pass_n.isdigit() else 0
    unit_fail = int(p_fail_n) if p_fail_n.isdigit() else 0
    java_unit_fails = int(j_fail) + int(j_err) if j_fail.isdigit() and j_err.isdigit() else 0

    verdict = "PRODUCTION READY"
    if (
        svc_pass < 5
        or crud_pass < 5
        or ai_pass < 4
        or k_pass < 3
        or not java_ok
        or not py_ok
        or issues
    ):
        verdict = "NEEDS FIXES"

    pr("")
    pr("=" * 55)
    pr("          SMARTHIRE FULL SYSTEM TEST REPORT")
    pr(f"          Run at: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    pr("=" * 55)
    pr("")
    pr("SERVICE HEALTH")
    pr(f"  Spring Boot (8080)     : {'UP' if spring_up else 'DOWN'}")
    pr(f"  Python FastAPI (8000)  : {'UP' if py_up else 'DOWN'}")
    pr(f"  PostgreSQL             : {pg_health}")
    pr(f"  Kafka                  : {kafka_health}")
    pr(f"  Swagger (Java)         : {swagger_java}")
    pr(f"  Swagger (Python)       : {swagger_py}")
    pr("")
    pr(f"AUTH TESTS                 : {auth_pass}/4 passed")
    pr(f"CRUD TESTS                 : {crud_pass}/5 passed")
    pr(f"AI SCREENING TESTS         : {ai_pass}/4 passed")
    pr(f"KAFKA TESTS                : {k_pass}/3 passed")
    pr(f"JAVA UNIT TESTS            : {j_tests} run, failures={j_fail}, errors={j_err}")
    pr(f"PYTHON UNIT TESTS          : {p_pass_n} passed, {p_fail_n} failed")
    pr("")
    pr("COVERAGE")
    pr(f"  Java                   : {java_cov}")
    pr(f"  Python                 : {py_cov}")
    pr("")
    pr("CONTAINER HEALTH")
    pr(f"  Memory                 : {'HIGH' if mem_high else 'OK'}")
    pr(f"  CPU                    : {'HIGH' if cpu_high else 'OK'}")
    pr(f"  Log errors             : {'FOUND' if log_err else 'NONE'}")
    pr("")
    pr("-" * 55)
    pr(f"TOTAL API TESTS            : {total_api}/{total_api_max} passed")
    pr(
        f"TOTAL UNIT TESTS           : Java ok={java_ok}, Python {p_pass_n} passed / {p_fail_n} failed"
    )
    pr("-" * 55)
    pr("")
    pr("ISSUES FOUND:")
    if not issues:
        pr("  None")
    else:
        for i, (msg, fix) in enumerate(issues, 1):
            pr(f"  {i}. {msg} -> Fix: {fix}")
    pr("")
    pr("WARNINGS:")
    if not warnings:
        pr("  None")
    else:
        for i, w in enumerate(warnings, 1):
            pr(f"  {i}. {w}")
    pr("")
    pr("=" * 55)
    pr(f"  VERDICT: {verdict}")
    pr("=" * 55)
    if verdict == "PRODUCTION READY":
        pr("  All systems go. Push to GitHub, update your resume,")
        pr("  and add SmartHire to your portfolio.")
    else:
        pr("  Fix the issues above then re-run this prompt.")
    pr("=" * 55)

    with open(LOG, "w", encoding="utf-8") as f:
        f.write("\n".join(lines_out))

    return 0 if verdict == "PRODUCTION READY" else 1


if __name__ == "__main__":
    sys.exit(main())
