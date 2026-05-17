"""Unit tests for RAGService — ChromaDB store and similarity search."""
import pytest
from unittest.mock import MagicMock, patch
from langchain_core.documents import Document
from services.rag_service import RAGService


class TestRAGService:

    @pytest.fixture
    def mock_chroma_collection(self):
        """Mock ChromaDB collection with add() and query()."""
        collection = MagicMock()
        return collection

    @pytest.fixture
    def mock_embeddings(self):
        """Mock HuggingFaceEmbeddings."""
        mock = MagicMock()
        mock.embed_query = MagicMock(return_value=[0.1] * 384)
        return mock

    def test_store_candidate_success(self, mock_chroma_collection, mock_embeddings):
        """store_candidate calls ChromaDB add with correct document."""
        with patch('services.rag_service.Chroma') as MockChroma:
            mock_client = MagicMock()
            MockChroma.return_value = MagicMock(
                _collection=mock_chroma_collection,
                add_documents=MagicMock()
            )


            with patch('services.rag_service.HuggingFaceEmbeddings', return_value=mock_embeddings):
                with patch('services.rag_service.ChatGroq'):
                    service = RAGService.__new__(RAGService)
                    service.vectorstore = MagicMock()
                    service.embeddings = mock_embeddings
                    service.llm = MagicMock()

                    service.store_candidate("cand-42", "Experienced Java developer")

                    service.vectorstore.add_documents.assert_called_once()
                    call_args = service.vectorstore.add_documents.call_args
                    docs = call_args[0][0]
                    assert len(docs) == 1
                    assert docs[0].page_content == "Experienced Java developer"
                    assert docs[0].metadata == {"candidate_id": "cand-42"}

    def test_retrieve_similar_candidates_returns_max_3(self):
        """find_similar returns at most top_k results."""
        mock_vs = MagicMock()
        mock_vs.similarity_search = MagicMock(return_value=[
            MagicMock(page_content="Resume 1", metadata={"candidate_id": "c1"}, id="0"),
            MagicMock(page_content="Resume 2", metadata={"candidate_id": "c2"}, id="1"),
            MagicMock(page_content="Resume 3", metadata={"candidate_id": "c3"}, id="2"),
        ])

        service = RAGService.__new__(RAGService)
        service.vectorstore = mock_vs

        results = service.find_similar("Java developer needed", top_k=3)

        mock_vs.similarity_search.assert_called_once_with("Java developer needed", k=3)
        assert len(results) == 3
        assert results[0]["candidate_id"] == "c1"
        assert results[1]["candidate_id"] == "c2"
        assert results[2]["candidate_id"] == "c3"

    def test_retrieve_similar_returns_empty_when_no_matches(self):
        """find_similar returns empty list when ChromaDB returns nothing."""
        mock_vs = MagicMock()
        mock_vs.similarity_search = MagicMock(return_value=[])

        service = RAGService.__new__(RAGService)
        service.vectorstore = mock_vs

        results = service.find_similar("nonexistent skill xyz", top_k=3)

        assert results == []
        mock_vs.similarity_search.assert_called_once()

    def test_store_candidate_chroma_down_handles_error(self):
        """ChromaDB failure in store_candidate → exception handled gracefully."""
        mock_vs = MagicMock()
        mock_vs.add_documents = MagicMock(side_effect=Exception("ChromaDB connection refused"))

        service = RAGService.__new__(RAGService)
        service.vectorstore = mock_vs

        # Should not raise — error is caught and logged
        try:
            service.store_candidate("cand-fail", "Some resume text")
        except Exception as e:
            pytest.fail(f"store_candidate should not raise, got: {e}")

    def test_find_similar_chroma_down_handles_error(self):
        """ChromaDB failure in find_similar → returns empty list gracefully."""
        mock_vs = MagicMock()
        mock_vs.similarity_search = MagicMock(side_effect=Exception("ChromaDB error"))

        service = RAGService.__new__(RAGService)
        service.vectorstore = mock_vs

        results = service.find_similar("some query", top_k=3)
        assert results == []

    def test_find_similar_includes_metadata_in_results(self):
        """Result dict includes candidate_id and resume_text from metadata."""
        mock_doc = MagicMock()
        mock_doc.page_content = "Java developer, 5 years experience"
        mock_doc.metadata = {"candidate_id": "cand-java-99"}

        mock_vs = MagicMock()
        mock_vs.similarity_search = MagicMock(return_value=[mock_doc])

        service = RAGService.__new__(RAGService)
        service.vectorstore = mock_vs

        results = service.find_similar("Java developer", top_k=1)

        assert len(results) == 1
        assert results[0]["candidate_id"] == "cand-java-99"
        assert results[0]["resume_text"] == "Java developer, 5 years experience"