import json
import time
import threading
from confluent_kafka import Consumer, KafkaError, KafkaException
from dotenv import load_dotenv
import os

load_dotenv()

consumer = None
thread = None
shutdown_event = None


def start_consumer(rag_service):
    global consumer, thread, shutdown_event

    shutdown_event = threading.Event()

    def consume_with_retry():
        global consumer
        retry_delay = 5
        max_delay = 60
        attempt = 0

        while not shutdown_event.is_set():
            try:
                consumer = Consumer({
                    "bootstrap.servers": os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"),
                    "group.id": "ai-service-group",
                    "auto.offset.reset": "earliest",
                    "session.timeout.ms": 30000,
                    "heartbeat.interval.ms": 10000,
                })
                consumer.subscribe(["resume-screening"])
                retry_delay = 5
                attempt = 0
                print("Kafka consumer connected and subscribed to 'resume-screening'")

                while not shutdown_event.is_set():
                    msg = consumer.poll(1.0)
                    if msg is None:
                        continue
                    if msg.error():
                        if msg.error().code() == KafkaError._PARTITION_EOF:
                            continue
                        if msg.error().code() == KafkaError._TRANSPORT:
                            raise KafkaException(msg.error())
                        print(f"Consumer error: {msg.error()}")
                        continue

                    try:
                        event = json.loads(msg.value().decode("utf-8"))
                        candidate_id = event.get("candidate_id")
                        resume_text = event.get("resume_text")
                        job_description = event.get("job_description")

                        if not all([candidate_id, resume_text, job_description]):
                            print(f"Invalid event: {event}")
                            continue

                        rag_service.store_candidate(candidate_id, resume_text)

                        from services.scoring_service import ScoringService
                        scoring_service = ScoringService(rag_service)

                        import asyncio

                        async def run():
                            return await scoring_service.screen(resume_text, job_description)

                        result = asyncio.run(run())
                        result["candidate_id"] = candidate_id
                        print(f"Processed screening for {candidate_id}: score={result.get('score')}")

                    except Exception as e:
                        print(f"Error processing message: {e}")

            except (KafkaException, OSError) as e:
                if shutdown_event.is_set():
                    break
                attempt += 1
                print(f"Kafka connection failed (attempt {attempt}): {e}. Retrying in {retry_delay}s...")
                if consumer:
                    try:
                        consumer.close()
                    except Exception:
                        pass
                    consumer = None
                time.sleep(retry_delay)
                retry_delay = min(retry_delay * 2, max_delay)

    thread = threading.Thread(target=consume_with_retry, daemon=True)
    thread.start()


def stop_consumer():
    global consumer, shutdown_event
    if shutdown_event:
        shutdown_event.set()
    if consumer:
        try:
            consumer.close()
        except Exception as e:
            print(f"Error closing consumer: {e}")
