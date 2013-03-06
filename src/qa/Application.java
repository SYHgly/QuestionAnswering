package qa;

import java.io.File;
import java.lang.ClassNotFoundException;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import qa.classifier.QuestionClassifier;
import qa.classifier.QuestionClassifierImpl;
import qa.extractor.AnswerExtractor;
import qa.factory.AnswerExtractorFactory;
import qa.factory.AnswerExtractorFactoryImpl;
import qa.factory.DocumentIndexerFactory;
import qa.factory.DocumentIndexerFactoryImpl;
import qa.factory.DocumentRetrieverFactory;
import qa.factory.DocumentRetrieverFactoryImpl;
import qa.factory.PassageRetrieverFactory;
import qa.factory.PassageRetrieverFactoryImpl;
import qa.factory.QuestionParserFactory;
import qa.factory.QuestionParserFactoryImpl;
import qa.factory.SearchEngineFactory;
import qa.factory.SearchEngineFactoryImpl;
import qa.helper.QuestionClassifierHelper;
import qa.indexer.DocumentIndexer;
import qa.model.AnswerInfo;
import qa.model.ClassifierTrainingInfo;
import qa.model.Document;
import qa.model.Passage;
import qa.model.QuestionInfo;
import qa.model.ResultInfo;
import qa.parser.QuestionParser;
import qa.search.DocumentRetriever;
import qa.search.PassageRetriever;
import qa.search.SearchEngine;

public class Application {

	public static Properties Settings;

	/**
	 * @param args
	 *            array of input questions
	 */
	public static void main(String[] args) {
		loadProperties();
		if (args.length == 0) {
			printUsage();
			return;
		}

		if (args[0].equals("-train")) {
			generateClassifierTrainingInfo();
		} else if (args[0].equals("-qc")) {
			classifyQuestions(args);
		} else {
			answer(args);
		}

	}

	private static void answer(String[] args) {
		// use factory pattern to create components so that we can easily
		// swap their underlying implementations later without changing
		// this code

		// create question parser
		QuestionParserFactory qpFactory = new QuestionParserFactoryImpl();
		QuestionParser questionParser = qpFactory.createQuestionParser();

		// create search engine
		SearchEngineFactory seFactory = new SearchEngineFactoryImpl();
		SearchEngine searchEngine = seFactory.createSearchEngine();

		// create document indexer
		DocumentIndexerFactory diFactory = new DocumentIndexerFactoryImpl();
		DocumentIndexer documentIndexer = diFactory.createDocumentIndexer();

		// create document retriever, associate it with document indexer and
		// import data set
		DocumentRetrieverFactory drFactory = new DocumentRetrieverFactoryImpl();
		DocumentRetriever documentRetriever = drFactory
				.createDocumentRetriever();
		documentRetriever.setIndexer(documentIndexer);
		documentRetriever.importDocuments(Application.Settings
				.getProperty("DOCUMENT_PATH"));

		// create passage retriever
		PassageRetrieverFactory prFactory = new PassageRetrieverFactoryImpl();
		PassageRetriever passageRetriever = prFactory.createPassageRetriever();

		// create answer extractor
		AnswerExtractorFactory aeFactory = new AnswerExtractorFactoryImpl();
		AnswerExtractor answerExtractor = aeFactory.createAnswerExtractor();

		// get answers for each input question
		for (String question : args) {
			// parse question to get expanded query and query type
			QuestionInfo questionInfo = questionParser.parse(question);

			// use search engine to search for possible answers and rank
			// them
			List<AnswerInfo> rankedAnswers = searchEngine.search(questionInfo);

			// initialize variables to store answers
			List<ResultInfo> results = new ArrayList<ResultInfo>();

			// for each answer returned from search engine, we try to
			// project
			// it to given data set by doing document retrieval using terms
			// from the answer
			for (AnswerInfo answerInfo : rankedAnswers) {
				// get set of relevant documents based on answer query
				List<Document> relevantDocs = documentRetriever
						.getDocuments(answerInfo.getAnswerTerms());

				// from this set of document, narrow down result set by
				// filter
				// only passages that possibly contain answer type
				List<Passage> relevantPassages = new ArrayList<Passage>();
				for (Document document : relevantDocs) {
					relevantPassages.addAll(passageRetriever.getPassages(
							document, answerInfo));
				}

				// extract ranked answers from relevant passages
				results.addAll(answerExtractor.extractAnswer(relevantPassages,
						questionInfo, answerInfo));
			}

			// print out results for this question
			printResults(question, results);
		}
	}

	private static void classifyQuestions(String[] args) {
		if (args.length < 2) {
			return;
		}

		ClassifierTrainingInfo trainingInfo = loadClassifierTrainingInfo();
		QuestionClassifier qc = new QuestionClassifierImpl();
		for (int i = 1; i < args.length; i++) {
			String question = args[i];
			QuestionClassifierHelper helper = QuestionClassifierHelper
					.getInstance();
			System.out.printf("\nQ: \"%s\"\n", question);
			System.out
					.printf("Classified as: %s\n", qc.apply(
							helper.getAllQueryTypes(), trainingInfo, question));

		}
	}

	private static void printUsage() {
		System.out
				.println("Usage: java Application <options> \"<question1>\" \"<question2>\"... ");
		System.out.println("where possible options include:");
		System.out.printf("  %-15s %s\n", "-train",
				"Only train question classifier, no input questions required");
		System.out.printf("  %-15s %s\n", "-qc", "Only classifiy questions");
		System.out.println();
		System.out
				.println("Run configuration stored in 'bin/Application.properties'");
		System.out
				.println("Example: java Application \"Where is Milan ?\" \"Who developed the Macintosh computer ?\" ");
	}

	private static void printResults(String question, List<ResultInfo> results) {
		System.out.printf("\nQ: \"%s\"\n", question);
		System.out.println("A(s):");
		for (ResultInfo resultInfo : results) {
			System.out.printf("[%-5s] %s\n", resultInfo.getSupportingDocument()
					.getId(), resultInfo.getAnswer());
		}
	}

	private static void generateClassifierTrainingInfo() {
		QuestionClassifierHelper helper = QuestionClassifierHelper
				.getInstance();
		List<QuestionInfo> trainingData = helper
				.getTrainingData(Application.Settings
						.getProperty("CORPUS_PATH"));
		QuestionClassifier qc = new QuestionClassifierImpl();
		ClassifierTrainingInfo trainingInfo = qc.train(
				helper.getAllQueryTypes(), trainingData);

		try {
			File classifierOutput = new File(
					Application.Settings.getProperty("CLASSIFIER_PATH"));
			if (!classifierOutput.isFile()) {
				classifierOutput.createNewFile();
			}

			FileOutputStream f_out = new FileOutputStream(
					Application.Settings.getProperty("CLASSIFIER_PATH"));
			ObjectOutputStream obj_out = new ObjectOutputStream(f_out);
			obj_out.writeObject(trainingInfo);
			obj_out.close();
		} catch (FileNotFoundException fnfe) {

		} catch (IOException ioe) {

		}

		System.out.println(trainingInfo);
		System.out.println("Training all done!");
	}

	private static ClassifierTrainingInfo loadClassifierTrainingInfo() {
		try {
			FileInputStream f_in = new FileInputStream(
					Application.Settings.getProperty("CLASSIFIER_PATH"));
			ObjectInputStream obj_in = new ObjectInputStream(f_in);
			Object obj = obj_in.readObject();
			obj_in.close();
			if (obj instanceof ClassifierTrainingInfo) {
				return (ClassifierTrainingInfo) obj;
			}
		} catch (FileNotFoundException fnfe) {

		} catch (IOException ioe) {

		} catch (ClassNotFoundException cnfe) {

		}

		return null;
	}

	private static void loadProperties() {
		String propertiesPath = new File(Application.class
				.getProtectionDomain().getCodeSource().getLocation().getPath())
				+ File.separator
				+ ".."
				+ File.separator
				+ "Application.properties";
		Settings = new Properties();
		try {
			Settings.load(new FileInputStream(propertiesPath));
			// for(String key : Settings.stringPropertyNames()) {
			// String value = Settings.getProperty(key);
			// System.out.println(key + " => " + value);
			// }
		} catch (IOException e) {

		}
	}
}
