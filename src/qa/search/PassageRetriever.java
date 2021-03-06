package qa.search;

import java.util.List;
import qa.model.Passage;

/**
 * Interface for passage retriever, which segments document into passages,
 * retrieve and rank relevant passages for a given query
 */
public interface PassageRetriever {
	/**
	 * Retrieves list of relevant passages for given query
	 * @param answerInfo query information
	 * @return list of ranked relevant passages
	 */
	List<Passage> getPassages(String answerInfo);
}
