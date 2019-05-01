package insilicopcr;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.RowConstraints;
import javafx.scene.control.TextField;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.event.ActionEvent;
import javafx.stage.FileChooser;
import javafx.stage.DirectoryChooser;
import javafx.scene.control.TextArea;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ComboBox;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Collections;
import java.util.ArrayList;
import java.util.HashMap;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import javafx.concurrent.Task;

public class MainRun extends Application {
	
	private final String version = "v0.100";
	
	private String sep = "/";
	
	private File inputFile = null, outDir = null, referenceFile = null;
	private int threads = Runtime.getRuntime().availableProcessors();
	private File primerDir, matchesDir, detailedDir, consolidatedDir;
	private File BBToolsLocation, BLASTLocation;
	private int mismatches = 1;
	private TextArea outputField;
	private HashMap<String, String> primerDict = new HashMap<String, String>();
	private HashMap<String, Sample> sampleDict = new HashMap<String, Sample>();
	
	public void start(Stage primaryStage) {
		if(System.getProperties().getProperty("os.name").contains("Windows")) {
			this.sep = "\\";
		}
		
		GridPane pane = new GridPane();
		ColumnConstraints cC = new ColumnConstraints();
		cC.setPercentWidth(2);
		List<ColumnConstraints> colCopies = Collections.nCopies(50, cC);
		pane.getColumnConstraints().addAll(colCopies);
		RowConstraints rC = new RowConstraints();
		rC.setPercentHeight(2);
		List<RowConstraints> rowCopies = Collections.nCopies(50, rC);
		pane.getRowConstraints().addAll(rowCopies);
		
		Text inputPrompt = new Text("Input a fasta file containing your input");
		inputPrompt.getStyleClass().add("prompt");
		HBox inputBox = new HBox(10);
		inputBox.setPrefSize(Double.MAX_VALUE, Double.MAX_VALUE);
		inputBox.getChildren().add(inputPrompt);
		pane.add(inputBox, 2, 2, 20, 2);
		
		TextField inputField = new TextField();
		inputField.setPrefSize(Double.MAX_VALUE, Double.MAX_VALUE);
		inputField.setEditable(false);
		pane.add(inputField, 2, 4, 30, 2);
		
		RadioButton isDirectory = new RadioButton();
		isDirectory.setText("Input is a directory");
		isDirectory.setSelected(false);
		isDirectory.setAlignment(Pos.CENTER);
		pane.add(isDirectory, 3, 7, 10, 2);
		
		Button inputBrowse = new Button("Browse...");
		inputBrowse.setPrefSize(Double.MAX_VALUE, Double.MAX_VALUE);
		pane.add(inputBrowse,  34, 4, 4, 2);
		inputBrowse.setOnAction(new EventHandler<ActionEvent>() {
			public void handle(ActionEvent e) {
				// Give them the option to put either a file or a directory as input
				if(!isDirectory.isSelected()) {
					FileChooser chooser = new FileChooser();
					inputFile = chooser.showOpenDialog(primaryStage);
					inputField.setText(inputFile.getAbsolutePath());
				}else {
					DirectoryChooser chooser = new DirectoryChooser();
					inputFile = chooser.showDialog(primaryStage);
					inputField.setText(inputFile.getAbsolutePath());
				}
			}
		});
		
		RadioButton faFQ = new RadioButton();
		faFQ.setText("Input is in fastq format");
		faFQ.setSelected(false);
		faFQ.setAlignment(Pos.CENTER);
		pane.add(faFQ, 13, 7, 10, 2);
		
		RadioButton pairedButton = new RadioButton();
		pairedButton.setText("FastQ files are paired");
		pairedButton.setDisable(true);
		pairedButton.setAlignment(Pos.CENTER);
		pane.add(pairedButton, 23, 7, 10, 2);
		
		isDirectory.selectedProperty().addListener(new ChangeListener<Boolean>() {
			public void changed(ObservableValue<? extends Boolean> b, Boolean oldVal, Boolean newVal) {
				if(faFQ.isSelected() && newVal == true) {
					pairedButton.setDisable(false);
				}else {
					pairedButton.setDisable(true);
				}
				if(pairedButton.isDisabled()) {
					pairedButton.setSelected(false);
				}
			}
		});
	
		faFQ.selectedProperty().addListener(new ChangeListener<Boolean>() {
			public void changed(ObservableValue<? extends Boolean> b, Boolean oldVal, Boolean newVal) {
				if(isDirectory.isSelected() && newVal == true) {
					pairedButton.setDisable(false);
				}else {
					pairedButton.setDisable(true);
				}
				if(pairedButton.isDisabled()) {
					pairedButton.setSelected(false);
				}
			}
		});
		
		Text outPrompt = new Text("Input a directory to contain the output");
		outPrompt.getStyleClass().add("prompt");
		HBox outputBox = new HBox(10);
		outputBox.setPrefSize(Double.MAX_VALUE, Double.MAX_VALUE);
		outputBox.getChildren().add(outPrompt);
		pane.add(outputBox, 2, 10, 20, 2);
		
		TextField outField = new TextField();
		outField.setPrefSize(Double.MAX_VALUE, Double.MAX_VALUE);
		outField.setEditable(false);
		pane.add(outField, 2, 12, 30, 2);
		
		Button outBrowse = new Button("Browse...");
		outBrowse.setPrefSize(Double.MAX_VALUE, Double.MAX_VALUE);
		pane.add(outBrowse, 34, 12, 4, 2);
		outBrowse.setOnAction(new EventHandler<ActionEvent>() {
			public void handle(ActionEvent e) {
				DirectoryChooser chooser = new DirectoryChooser();
				outDir = chooser.showDialog(primaryStage);
				outField.setText(outDir.getAbsolutePath());
			}
		});
		
		Text refPrompt = new Text("Input a primer reference file in fasta format");
		refPrompt.getStyleClass().add("prompt");
		HBox refBox = new HBox(10);
		refBox.setPrefSize(Double.MAX_VALUE, Double.MAX_VALUE);
		refBox.getChildren().add(refPrompt);
		pane.add(refPrompt, 2, 15, 20, 2);
		
		TextField refField = new TextField();
		refField.setPrefSize(Double.MAX_VALUE, Double.MAX_VALUE);
		refField.setEditable(false);
		pane.add(refField, 2, 17, 30, 2);
		
		Button refBrowse = new Button("Browse...");
		refBrowse.setPrefSize(Double.MAX_VALUE, Double.MAX_VALUE);
		pane.add(refBrowse, 34, 17, 4, 2);
		refBrowse.setOnAction(new EventHandler<ActionEvent>() {
			public void handle(ActionEvent e) {
				FileChooser chooser = new FileChooser();
				referenceFile = chooser.showOpenDialog(primaryStage);
				refField.setText(referenceFile.getAbsolutePath());
			}
		});
		
		outputField = new TextArea();
		outputField.setPrefSize(Double.MAX_VALUE,Double.MAX_VALUE);
		outputField.setEditable(false);
		pane.add(outputField, 2, 21, 46, 22);
		
		Text threadPrompt = new Text("Input number of\nthreads to use(1, 2, etc.)");
		threadPrompt.setTextAlignment(TextAlignment.CENTER);
		HBox threadBox = new HBox(10);
		threadBox.setAlignment(Pos.CENTER);
		threadBox.getChildren().add(threadPrompt);
		pane.add(threadBox, 40, 5, 8, 3);
		
		ComboBox<Integer> threadField = new ComboBox<Integer>();
		threadField.setPrefSize(Double.MAX_VALUE, Double.MAX_VALUE);
		for(int i = 0; i < Runtime.getRuntime().availableProcessors() - 1; i++) {
			threadField.getItems().add(i + 1);
		}
		threadField.getSelectionModel().selectLast();
		threadField.setVisibleRowCount(3);
		pane.add(threadField, 41, 8, 6, 3);
		
		Text mismatchPrompt = new Text("Input number of\npermitted mismatches");
		mismatchPrompt.setTextAlignment(TextAlignment.CENTER);
		HBox mismatchBox = new HBox(10);
		mismatchBox.setAlignment(Pos.CENTER);
		mismatchBox.getChildren().add(mismatchPrompt);
		pane.add(mismatchBox, 40, 12, 8, 3);
		
		ComboBox<Integer> mismatchField = new ComboBox<Integer>();
		mismatchField.setPrefSize(Double.MAX_VALUE, Double.MAX_VALUE);
		mismatchField.getItems().addAll(1, 2, 3);
		mismatchField.getSelectionModel().selectFirst();
		pane.add(mismatchField, 41, 15, 6, 3);
		
		Text alertText = new Text();
		alertText.setStyle("-fx-fill: red;");
		HBox alertBox = new HBox(10);
		alertBox.setPrefSize(Double.MAX_VALUE, Double.MAX_VALUE);
		alertBox.setAlignment(Pos.CENTER);
		alertBox.getChildren().add(alertText);
		pane.add(alertBox, 1, 43, 48, 2);
		
		Button proceed = new Button("Proceed");
		proceed.setPrefSize(Double.MAX_VALUE, Double.MAX_VALUE);
		pane.add(proceed, 22, 45, 5, 3);
		proceed.setOnAction(new EventHandler<ActionEvent>() {
			public void handle(ActionEvent e) {
				threads = threadField.getSelectionModel().getSelectedItem();
				mismatches = mismatchField.getSelectionModel().getSelectedItem();
				if(inputFile == null || outDir == null || referenceFile == null) {
					alertText.setText("Please enter an input file, a reference file, and an output file");
					e.consume();
				}else if((!inputFile.isDirectory() && !Methods.verifyFastaFormat(inputFile)) || !Methods.verifyFastaFormat(referenceFile)) {
					alertText.setText("Input and primer files must be in fasta format");
					e.consume();
				}else if(pairedButton.isSelected() && inputFile.isDirectory() && inputFile.listFiles().length % 2 != 0) {
					alertText.setText("If using paired reads, ensure the input directory only contains appropriately paired reads");
					e.consume();
				}else {
					if(inputFile.isDirectory()) {
						if(!isDirectory.isSelected()) {
							alertText.setText("If the input file is a directory, please select the \"Input is a Directory\" button");
							e.consume();
							return;
						}
						else {
							for(File item : inputFile.listFiles()) {
								if(!Methods.verifyFastaFormat(item)) {
									alertText.setText("Input directory contains non-fastq/fasta format files");
									e.consume();
									return;
								}
							}
						}
					}
					alertText.setText("");
					RunPCRTask task = new RunPCRTask(outputField);
					Thread t = new Thread(task);
					t.start();
				}
			}
		});
		
		Scene scene = new Scene(pane, 800, 500);
		scene.getStylesheets().add("MainRun.css");
		primaryStage.setScene(scene);
		primaryStage.setTitle("InSilico PCR " + version);
		primaryStage.show();
	}
	
	// Makes directories within the output directory
	public void makeDirectories() {
		primerDir = new File(outDir + sep + "primers");
		primerDir.mkdirs();
		matchesDir = new File(outDir + sep + "matches");
		matchesDir.mkdirs();
		detailedDir = new File(outDir.getAbsolutePath() + sep + "detailed_report");
		detailedDir.mkdirs();
		consolidatedDir = new File(outDir.getAbsolutePath() + sep + "consolidated_report");
		consolidatedDir.mkdirs();
	}
	
	// Find dependencies
	public void findDependencies() {
		Path dir = Paths.get(System.getProperties().getProperty("user.dir"));
//		Path dir = new File("C:\\Users\\ChmaraJ\\Desktop").toPath();
		Find.Finder finder = new Find.Finder("**bbmap", dir);
		for(Path path: finder.run()) {
			File directory = path.toFile();
			for(File file : directory.listFiles()) {
				if(file.getName().contains("tadpole.sh")) {
					BBToolsLocation = path.toFile();
				}
			}
		}
		
		Path dir2 = Paths.get(System.getProperties().getProperty("user.dir"));
//		Path dir2 = new File("C:\\Users\\ChmaraJ\\Desktop").toPath();
		Find.Finder finder2 = new Find.Finder("**makeblastdb.exe", dir2);
		for(Path path : finder2.run()) {
			File blastdbpath = path.toFile();
			BLASTLocation = blastdbpath.getParentFile();
		}
	}
	
	// Main body of the pipeline, runs the contained methods in order
	public class RunPCRTask extends Task<Void> {
		
		private TextArea outputField;
		
		public RunPCRTask(TextArea outputField) {
			this.outputField = outputField;
		}
		
		public Void call() {
			Methods.logMessage(outputField, "Beginning Program Run");
			findDependencies();
			Methods.logMessage(outputField, "Found Dependencies");
			makeDirectories();
			Methods.logMessage(outputField, "Created Directories");
			sampleDict = Methods.createSampleDict(inputFile);
			Methods.logMessage(outputField, "Created Sample Dictionary");
			primerDict = Methods.parseFastaToDictionary(referenceFile);
			Methods.logMessage(outputField, "Created Primer Dictionary");
			Methods.processPrimers(primerDict, outputField, outDir, sep);
			Methods.logMessage(outputField, "Finished Formatting Primers");
			// Done to here
			// Just get a file and check if it is fastq, only have to bait and run assembly on fastq files
			if(sampleDict.get(sampleDict.keySet().toArray(new String[sampleDict.keySet().size()])[0]).getFileType().equals("fastq")) {
				System.out.println("Baiting reads");
				runBaitTask(outputField);
				Methods.logMessage(outputField, "Completed First Baiting");
				runSecondBaitTask(outputField);
				Methods.logMessage(outputField, "Completed Second Baiting");
				runAssembleTask(outputField);
				Methods.logMessage(outputField, "Completed Assembly");
			}
			Methods.makeBlastDB(new File(outDir.getAbsolutePath() + sep + "primer_tmp.fasta"), BLASTLocation);
			Methods.logMessage(outputField, "Completed Database Creation");
			// If files were fastq, need to use the assembly file instead of raw files
			runBLASTTask(outputField);
			Methods.logMessage(outputField, "Completed BLAST");
			Methods.parseBlastOutput(consolidatedDir, detailedDir, primerDict, mismatches, sampleDict);
			Methods.logMessage(outputField, "Parsed BLAST output");
			Methods.makeConsolidatedReport(consolidatedDir, sep, sampleDict);
			Methods.logMessage(outputField, "Created Consolidated Report");
			return null;
		}
	}
	
	//Method to make a thread to run the CountMatchTask to prevent UI from hanging
	public void runBaitTask(TextArea outputField) {
		BaitTask task = new BaitTask(outputField);
		Thread t = new Thread(task);
		t.setDaemon(true);
		t.start();
		try {
			t.join();
		}catch(InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	//Method to make a thread to run the SeconBaitTask to prevent UI from hanging
	public void runSecondBaitTask(TextArea outputField) {
		SecondBaitTask task = new SecondBaitTask(outputField);
		Thread t = new Thread(task);
		t.setDaemon(true);
		t.start();
		try {
			t.join();
		}catch(InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	//Method to make a thread to run the AssemblyTask to prevent UI from hanging
	public void runAssembleTask(TextArea outputField) {
		AssembleTask task = new AssembleTask(outputField);
		Thread t = new Thread(task);
		t.setDaemon(true);
		t.start();
		try {
			t.join();
		}catch(InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	// Method to make a thread to run the BLASTTask to prevent UI from hanging
	public void runBLASTTask(TextArea outputField) {
		Thread t;
		if(sampleDict.get(sampleDict.keySet().iterator().next()).getFileType().equals("fastq")) {
			for(String key : sampleDict.keySet()) {
				BlastTask task = new BlastTask(outDir.getAbsolutePath() + sep + "primer_tmp.fasta", sampleDict.get(key).getAssemblyFile(),
						detailedDir, sep, threads, BLASTLocation);
				t = new Thread(task);
				t.setDaemon(true);
				t.start();
				try {
					t.join();
				}catch(InterruptedException e) {
					e.printStackTrace();
				}
			}
		}else {
			for(String key : sampleDict.keySet()) {
				for(String file : sampleDict.get(key).getFiles()) {
					BlastTask task = new BlastTask(outDir.getAbsolutePath() + sep + "primer_tmp.fasta", file,
							detailedDir, sep, threads, BLASTLocation);
					t = new Thread(task);
					t.setDaemon(true);
					t.start();
					try {
						t.join();
					}catch(InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}
	
	// Bait FastQ reads from input files using BBDuk and the primer file as the target
	private class BaitTask extends Task<Void> {
		
		private TextArea outputField;
		private String line;
		private MessageConsumer consumer;
		
		public BaitTask(TextArea outputField) {
			this.outputField = outputField;
		}
		
		public Void call() {
			
			// Need to make sure that whatever k-value is being used is no longer than the shortest primer length
			int klength = Integer.MAX_VALUE;
			for(String key : primerDict.keySet()) {
				if(primerDict.get(key).length() < klength) {
					klength = primerDict.get(key).length();
				}
			}
			
			String ref = outDir.getAbsolutePath() + sep + "primer_tmp.fasta";
			
			for(String key : sampleDict.keySet()) {
				
				BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();
				consumer = new MessageConsumer(messageQueue, this.outputField);
				
				Sample currentSample = sampleDict.get(key);
				File sampleDir = new File(detailedDir.getAbsolutePath() + sep + currentSample.getName());
				sampleDir.mkdirs();
				String fullProcessCall = "";
				if(currentSample.getFiles().size() == 2) {
					String options = " ref=" + ref + " k=" + klength + " in1=" + currentSample.getFiles().get(0) + " in2=" + currentSample.getFiles().get(1) + 
							" hdist=" + mismatches + " threads=" + threads + " interleaved=t outm=" + 
							sampleDir.getAbsolutePath() + sep + currentSample.getName() + "_targetMatches.fastq.gz";
					fullProcessCall = "java -ea -Xmx7g -cp ./current jgi.BBDuk" + options; 
				}else {
					String options = " ref=" + ref + " k=" + klength + " in=" + currentSample.getFiles().get(0) + 
							" hdist=" + mismatches + " threads=" + threads + " interleaved=t outm=" + 
							sampleDir.getAbsolutePath() + sep + currentSample.getName() + "_targetMatches.fastq.gz";
					fullProcessCall = "java -ea -Xmx7g -cp ./current jgi.BBDuk" + options;
				}
				try {
					Process p = Runtime.getRuntime().exec(fullProcessCall, null, BBToolsLocation);
					BufferedReader stdout = new BufferedReader(new InputStreamReader(p.getErrorStream()));
					Platform.runLater(() -> consumer.start());
					while((this.line = stdout.readLine()) != null) {
						try {
							messageQueue.put(this.line);
						}catch(InterruptedException e) {
							e.printStackTrace();
						}
					}
					consumer.stop();
					stdout.close();
					try {
						p.waitFor();
					}catch(InterruptedException e) {
						e.printStackTrace();
					}
				}catch(IOException e) {
					e.printStackTrace();
				}
			}
			return null;
		}
	}
	
	// Bait more FastQ read nearby the originally baited reads using the originally baited reads as bait themselves
	// If USERS find issues with memory overflow, can use qhdist instead of hdist. Sacrifices speed for memory by 
	// Conducting mutations on query instead of reference? Dramatically reduces memory usage. 
	private class SecondBaitTask extends Task<Void> {
		
		private TextArea outputField;
		private String line;
		private MessageConsumer consumer;
		
		public SecondBaitTask(TextArea outputField) {
			this.outputField = outputField;
		}
		
		public Void call() {
			for(String key : sampleDict.keySet()) {
				
				BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();
				consumer = new MessageConsumer(messageQueue, this.outputField);
				
				Sample currentSample = sampleDict.get(key);
				File sampleDir = new File(detailedDir.getAbsolutePath() + sep + currentSample.getName());
				String ref = sampleDir.getAbsolutePath() + sep + currentSample.getName() + "_targetMatches.fastq.gz";
				String fullProcessCall = "";
				if(currentSample.getFiles().size() == 2) {
					String options = " ref=" + ref + " in1=" + currentSample.getFiles().get(0) + " in2=" + currentSample.getFiles().get(1) + 
							" hdist=" + mismatches + " threads=" + threads + " interleaved=t outm=" +
							sampleDir.getAbsolutePath() + sep + currentSample.getName() + "_doubleTargetMatches.fastq.gz";
					fullProcessCall = "java -ea -Xmx7g -cp ./current jgi.BBDuk" + options;
				}else {
					String options = " ref=" + ref + " in=" + currentSample.getFiles().get(0) + 
							" hdist=" + mismatches + " threads=" + threads + " interleaved=t outm=" +
							sampleDir.getAbsolutePath() + sep + currentSample.getName() + "_doubleTargetMatches.fastq.gz";
					fullProcessCall = "java -ea -Xmx7g -cp ./current jgi.BBDuk" + options;
				}
				try {
					Process p = Runtime.getRuntime().exec(fullProcessCall, null, BBToolsLocation);
					BufferedReader stdout = new BufferedReader(new InputStreamReader(p.getErrorStream()));
					Platform.runLater(() -> consumer.start());
					while((this.line = stdout.readLine()) != null) {
						try {
							messageQueue.put(this.line);
						}catch(InterruptedException e) {
							e.printStackTrace();
						}
					}
					consumer.stop();
					stdout.close();
					try {
						p.waitFor();
					}catch(InterruptedException e) {
						e.printStackTrace();
					}
				}catch(IOException e) {
					e.printStackTrace();
				}
			}
			return null;
		}
	}
	
	// Assemble reads from both rounds of baiting to attempt to get long enough contigs to ensure as many primer hits are contained on the same contigs as possible
	private class AssembleTask extends Task<Void> {
		
		private TextArea outputField;
		private String line;
		private MessageConsumer consumer;
		
		public AssembleTask(TextArea outputField) {
			this.outputField = outputField;
		}
		
		public Void call() {
			for(String key : sampleDict.keySet()) {
				
				BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();
				consumer = new MessageConsumer(messageQueue, this.outputField);
				
				Sample currentSample = sampleDict.get(key);
				File sampleDir = new File(detailedDir.getAbsolutePath() + sep + currentSample.getName());
				
				String in = sampleDir.getAbsolutePath() + sep + currentSample.getName() + "_doubleTargetMatches.fastq.gz";
				String out = sampleDir.getAbsolutePath() + sep + currentSample.getName() + "_assembly.fasta";
				
				// Make sure that the sample contains a reference to its own assembly file
				currentSample.setAssemblyFile(out);
				
				
				String options = " in=" + in + " out=" + out + " threads=" + threads;
				String fullProcessCall = "java -ea -Xmx7g -cp ./current assemble.Tadpole" + options;
				try {
					Process p = Runtime.getRuntime().exec(fullProcessCall, null, BBToolsLocation);
					BufferedReader reader = new BufferedReader(new InputStreamReader(p.getErrorStream()));
					Platform.runLater(() -> consumer.start());
					while((line = reader.readLine()) != null) {
						try {
							messageQueue.put(line);
						}catch(InterruptedException e) {
							e.printStackTrace();
						}
					}
					consumer.stop();
					reader.close();
					try {
						p.waitFor();
					}catch(InterruptedException e) {
						e.printStackTrace();
					}
				}catch(IOException e) {
					e.printStackTrace();
				}
				
			}
			return null;
		}
	}
	
	// Run Blast on the provided primers and query, calls addHeaderToTSV on the resulting .tsv file
	public class BlastTask extends Task<Void> {
		
		private String primers;
		private String query;
		private File detailedDir;
		private String sep;
		private int threads;
		private File BLASTLocation;
		private MessageConsumer consumer;
		private String line;
		
		public BlastTask(String primers, String query, File detailedDir, String sep, int threads, File BLASTLocation) {
			this.primers = primers;
			this.query = query;
			this.detailedDir = detailedDir;
			this.sep = sep;
			this.threads = threads;
			this.BLASTLocation = BLASTLocation;
		}
		
		public Void call() {
			BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();
			consumer = new MessageConsumer(messageQueue, outputField);
			
			File file = new File(query);
			String name = file.getName().split("_assembly\\.fasta")[0];
			File blastOutput = new File(detailedDir.getAbsolutePath() + sep + name);
			blastOutput.mkdirs();
			File blastTSV = new File(blastOutput.getAbsolutePath() + sep + name + ".tsv");
			String options = " -task blastn-short -query " + query + " -db " + primers +
					" -evalue 1e-1 -num_alignments 1000000 -num_threads " + threads +
					" -outfmt \"6 qseqid sseqid positive mismatch gaps evalue bitscore slen length qstart qend qseq sstart send sseq\"" +
					" -out " + blastTSV;
			String fullProcessCall = "blastn.exe " + options;
			try {
				Process p = Runtime.getRuntime().exec(BLASTLocation.getAbsolutePath() + sep + fullProcessCall);
				BufferedReader reader = new BufferedReader(new InputStreamReader(p.getErrorStream()));
				Platform.runLater(() -> consumer.start());
				while((line = reader.readLine()) != null) {
					try {
						messageQueue.put(line);
					}catch(InterruptedException e) {
						e.printStackTrace();
					}
				}
				reader.close();
				consumer.stop();
				try {
					p.waitFor();
				}catch(InterruptedException e) {
					e.printStackTrace();
				}
			}catch(IOException e) {
				e.printStackTrace();
			}
			
			Methods.addHeaderToTSV(blastTSV);
			return null;
		}
	}
	
	public static void main(String[] args) {
		Application.launch(MainRun.class);
	}

}
