import chromadb
from langchain_chroma import Chroma
from langchain_groq import ChatGroq
from langchain_huggingface import HuggingFaceEmbeddings
from langchain_core.documents import Document
from dotenv import load_dotenv
import os

load_dotenv()


class RAGService:
    def __init__(self):
        self.embeddings = HuggingFaceEmbeddings(
            model_name="sentence-transformers/all-MiniLM-L6-v2"
        )
        self.vectorstore = Chroma(
            collection_name="candidates",
            embedding_function=self.embeddings,
            client=chromadb.PersistentClient(path="./chroma_data"),
        )
        self.llm = ChatGroq(
            model="llama-3.1-8b-instant",
            api_key=os.getenv("GROQ_API_KEY"),
        )

    def store_candidate(self, candidate_id: str, resume_text: str):
        try:
            docs = [Document(page_content=resume_text, metadata={"candidate_id": candidate_id})]
            self.vectorstore.add_documents(docs)
        except Exception as e:
            print(f"Error storing candidate in ChromaDB: {e}")

    def find_similar(self, text: str, top_k: int = 3):
        try:
            results = self.vectorstore.similarity_search(text, k=top_k)
            return [
                {
                    "candidate_id": doc.metadata.get("candidate_id"),
                    "resume_text": doc.page_content,
                    "score": doc.metadata.get("score"),
                }
                for doc in results
            ]
        except Exception as e:
            print(f"Error finding similar candidates in ChromaDB: {e}")
            return []
