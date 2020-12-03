package insilicopcr;

import dispatchpcr.Dispatcher;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
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
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.geometry.Pos;
import javafx.event.ActionEvent;
import javafx.stage.FileChooser;
import javafx.stage.DirectoryChooser;
import javafx.scene.control.TextArea;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;

import java.io.File;
import java.io.FileWriter;
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
//import java.util.concurrent.Executors;
//import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.swing.JOptionPane;

import javafx.concurrent.Task;

public class MainRun extends Application {
	
	public static String sep = File.separator;
	private double memJava;
	
	public ProgressBar BLASTProgress;
	private File inputFile = null, outDir = null, primerFile = null;
	private int threads = Runtime.getRuntime().availableProcessors();
	private File detailedDir, consolidatedDir;
	private File BBToolsLocation, BLASTLocation, JavaLocation;
	private String javaCall;
	private int mismatches = 1;
	private TextArea outputField;
	private HashMap<String, String> primerDict = new HashMap<String, String>();
	private HashMap<String, Sample> sampleDict = new HashMap<String, Sample>();
	private boolean fastqPresent = false;
	private ArrayList<Process> mainProcesses = new ArrayList<Process>();
	private boolean currentlyRunning = false;
	private ThreadPoolExecutor mainPool;
	private Button gelButton;
	
	public void start(Stage primaryStage) {
		if(System.getProperties().getProperty("os.name").contains("Windows")) {
			String[] command = {"wmic", "computersystem", "get", "TotalPhysicalMemory"};
			try {
				String line;
				ArrayList<String> output = new ArrayList<String>();
				Process p = Runtime.getRuntime().exec(command);
				BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
				while((line = reader.readLine()) != null) {
					output.add(line);
				}
				String fullOutput = String.join("", output);
				String trimmedOutput = fullOutput.split("\\s+")[1];
				memJava = Double.parseDouble(trimmedOutput) / 1000000000;
			}catch(IOException e) {
				e.printStackTrace();
			}
		}else {
			String[] command = {"grep", "MemTotal", "/proc/meminfo"};
			try {
				String line;
				ArrayList<String> output = new ArrayList<String>();
				Process p = new ProcessBuilder(command).start();
				BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
				while((line = reader.readLine()) != null) {
					output.add(line);
				}
				String fullOutput = String.join("",output);
				String trimmedOutput = fullOutput.split("\\s+")[1];
				memJava = Double.parseDouble(trimmedOutput) / 1000000000;
			}catch(IOException e) {
				e.printStackTrace();
			}
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
		
		Text inputPrompt = new Text("Input a fasta/fastq file or directory of fasta/fastq files containing your input");
		inputPrompt.getStyleClass().add("prompt");
		HBox inputBox = new HBox(10);
		inputBox.setPrefSize(Double.MAX_VALUE, Double.MAX_VALUE);
		inputBox.getChildren().add(inputPrompt);
		pane.add(inputBox, 2, 2, 20, 2);
		
		TextField inputField = new TextField();
		inputField.setPrefSize(Double.MAX_VALUE, Double.MAX_VALUE);
		inputField.setEditable(false);
		pane.add(inputField, 2, 4, 30, 2);
		inputField.setOnDragOver(new EventHandler<DragEvent>(){
			public void handle(DragEvent e) {
				if(e.getGestureSource() != inputField && e.getDragboard().hasFiles()) {
					e.acceptTransferModes(TransferMode.COPY_OR_MOVE);
				}
				e.consume();
			}
		});
		inputField.setOnDragDropped(new EventHandler<DragEvent>() {
			public void handle(DragEvent e) {
				Dragboard db = e.getDragboard();
				boolean success = false;
				if(db.hasFiles()) {
					List<File> listFiles = db.getFiles();
					inputFile = listFiles.get(0);
					inputField.setText(inputFile.getAbsolutePath());
					success = true;
				}
				e.setDropCompleted(success);
				e.consume();
			}
		});
		
		RadioButton isDirectory = new RadioButton();
		isDirectory.setText("Input is a directory");
		isDirectory.setSelected(false);
		isDirectory.setAlignment(Pos.CENTER);
		pane.add(isDirectory, 3, 7, 10, 2);
		
		Button inputBrowse = new Button("Browse...");
		inputBrowse.setPrefSize(Double.MAX_VALUE, Double.MAX_VALUE);
		pane.add(inputBrowse, 34, 4, 4, 2);
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
		
		Text outPrompt = new Text("Input a directory to contain the output. Path MUST NOT contain spaces");
		outPrompt.getStyleClass().add("prompt");
		HBox outputBox = new HBox(10);
		outputBox.setPrefSize(Double.MAX_VALUE, Double.MAX_VALUE);
		outputBox.getChildren().add(outPrompt);
		pane.add(outputBox, 2, 10, 20, 2);
		
		TextField outField = new TextField();
		outField.setPrefSize(Double.MAX_VALUE, Double.MAX_VALUE);
		outField.setEditable(false);
		pane.add(outField, 2, 12, 30, 2);
		outField.setOnDragOver(new EventHandler<DragEvent>(){
			public void handle(DragEvent e) {
				if(e.getGestureSource() != outField && e.getDragboard().hasFiles()) {
					e.acceptTransferModes(TransferMode.COPY_OR_MOVE);
				}
				e.consume();
			}
		});
		outField.setOnDragDropped(new EventHandler<DragEvent>() {
			public void handle(DragEvent e) {
				Dragboard db = e.getDragboard();
				boolean success = false;
				if(db.hasFiles()) {
					List<File> listFiles = db.getFiles();
					outDir = listFiles.get(0);
					outField.setText(outDir.getAbsolutePath());
					success = true;
				}
				e.setDropCompleted(success);
				e.consume();
			}
		});
		
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
		refField.setTooltip(new Tooltip("Please refer to the custom_primer_guide.txt for instructions on how to create a valid primer file"));
		refField.setPrefSize(Double.MAX_VALUE, Double.MAX_VALUE);
		refField.setEditable(false);
		pane.add(refField, 2, 17, 30, 2);
		refField.setOnDragOver(new EventHandler<DragEvent>(){
			public void handle(DragEvent e) {
				if(e.getGestureSource() != refField && e.getDragboard().hasFiles()) {
					e.acceptTransferModes(TransferMode.COPY_OR_MOVE);
				}
				e.consume();
			}
		});
		refField.setOnDragDropped(new EventHandler<DragEvent>() {
			public void handle(DragEvent e) {
				Dragboard db = e.getDragboard();
				boolean success = false;
				if(db.hasFiles()) {
					List<File> listFiles = db.getFiles();
					primerFile = listFiles.get(0);
					refField.setText(primerFile.getAbsolutePath());
					success = true;
				}
				e.setDropCompleted(success);
				e.consume();
			}
		});
		
		Button refBrowse = new Button("Browse...");
		refBrowse.setPrefSize(Double.MAX_VALUE, Double.MAX_VALUE);
		pane.add(refBrowse, 34, 17, 4, 2);
		refBrowse.setOnAction(new EventHandler<ActionEvent>() {
			public void handle(ActionEvent e) {
				FileChooser chooser = new FileChooser();
				primerFile = chooser.showOpenDialog(primaryStage);
				refField.setText(primerFile.getAbsolutePath());
			}
		});
		
		outputField = new TextArea();
		outputField.setPrefSize(Double.MAX_VALUE,Double.MAX_VALUE);
		outputField.setEditable(false);
		pane.add(outputField, 2, 21, 46, 22);
		
		Text threadPrompt = new Text("Input number of\nthreads to use(1, 2, etc.)");
		threadPrompt.getStyleClass().add("prompt");
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
		mismatchPrompt.getStyleClass().add("prompt");
		mismatchPrompt.setTextAlignment(TextAlignment.CENTER);
		HBox mismatchBox = new HBox(10);
		mismatchBox.setAlignment(Pos.CENTER);
		mismatchBox.getChildren().add(mismatchPrompt);
		pane.add(mismatchBox, 40, 12, 8, 3);
		
		ComboBox<Integer> mismatchField = new ComboBox<Integer>();
		mismatchField.setPrefSize(Double.MAX_VALUE, Double.MAX_VALUE);
		mismatchField.getItems().addAll(0, 1, 2, 3);
		mismatchField.getSelectionModel().selectFirst();
		pane.add(mismatchField, 41, 15, 6, 3);
		
		Text alertText = new Text();
		alertText.setStyle("-fx-fill: red;");
		HBox alertBox = new HBox(10);
		alertBox.setPrefSize(Double.MAX_VALUE, Double.MAX_VALUE);
		alertBox.setAlignment(Pos.CENTER);
		alertBox.getChildren().add(alertText);
		pane.add(alertBox, 1, 43, 48, 2);
		
//		gelButton = new Button("View Gel Image");
//		gelButton.setPrefSize(Double.MAX_VALUE, Double.MAX_VALUE);
//		gelButton.setOnAction(new EventHandler<ActionEvent>() {
//			public void handle(ActionEvent e) {
//				displayGelImage();
//			}
//		});
		
		Button proceed = new Button("Proceed");
		proceed.setPrefSize(Double.MAX_VALUE, Double.MAX_VALUE);
		pane.add(proceed, 22, 45, 5, 3);
		proceed.setOnAction(new EventHandler<ActionEvent>() {
			public void handle(ActionEvent e) {
				threads = threadField.getSelectionModel().getSelectedItem();
				mismatches = mismatchField.getSelectionModel().getSelectedItem();
				if(inputFile == null || outDir == null || primerFile == null) {
					alertText.setText("Please enter an input file, a reference file, and an output file");
					e.consume();
				}else if((!inputFile.isDirectory() && !Methods.verifyFastaFormat(inputFile)) || !Methods.verifyFastaFormat(primerFile)) {
					alertText.setText("Input and primer files must be in fasta format (or fastq for input)");
					e.consume();
				}else if(!Methods.verifyPrimerFile(primerFile)) {
					alertText.setText("A primer file which contains probe primers must also have fwd and rev primers with the same name");
					e.consume();
				}else {
					if(inputFile.isDirectory()) {
						if(Methods.noFastaFile(inputFile)) {
							alertText.setText("Input directory must contain at least one valid fastq/fasta file");
							e.consume();
						}
						for(File item : inputFile.listFiles()) {
							if(item.isDirectory() || (!item.getName().contains(".fasta") && !item.getName().contains(".fna") && 
									!item.getName().contains(".ffn") && !item.getName().contains(".fastq"))) {
								continue;
							}
							if(!Methods.verifyFastaFormat(item)) {
								alertText.setText("Input directory contains non-fastq/fasta format files");
								e.consume();
								return;
							}
						}
					}
					alertText.setText("");
					outputField.clear();
					RunPCRTask task = new RunPCRTask(outputField, pane);
					Thread t = new Thread(task);
					t.start();
					ProgressBar progress = new ProgressBar();
					progress.setPrefSize(Double.MAX_VALUE, Double.MAX_VALUE);
					progress.progressProperty().bind(task.progressProperty());
					pane.add(progress, 2, 43, 46, 2);
					BLASTProgress = new ProgressBar();
					BLASTProgress.setPrefSize(Double.MAX_VALUE, Double.MAX_VALUE);
					pane.add(BLASTProgress, 30, 46, 18, 2);
				}
			}
		});
		
		Scene scene = new Scene(pane, 800, 500); 
		scene.getStylesheets().add("src/resources/MainRun.css");
//		scene.getStylesheets().add(MainRun.class.getResource("/resources/MainRun.css").toString()); // For running in Eclipse
		primaryStage.setScene(scene);
		primaryStage.setOnCloseRequest(new EventHandler<WindowEvent>() {
			public void handle(WindowEvent e) {
				if(outDir == null || primerFile == null || inputFile == null || BBToolsLocation == null || BLASTLocation == null) {
					Methods.logMessage(outputField, "One of the input parameters, or one of the dependency locations, is not set");
					Platform.exit();
					System.exit(0);
				}
				if(currentlyRunning) {
					if(JOptionPane.showConfirmDialog(null, 
							"Are you sure you want to exit? If the program is running, output may become corrupted",
							"Exit CFIAssembly",
							JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION){
						File qaFile = new File(outDir.getAbsolutePath() + sep + "QAlog.txt");
						if(!qaFile.exists()) {
							Methods.makeQALog(qaFile, Dispatcher.version, outDir, inputFile, primerFile, BBToolsLocation, BLASTLocation);
						}
						try{
							FileWriter logFile = new FileWriter(outDir.getAbsolutePath() + sep + "log.txt", true);
							String[] outputLines = outputField.getText().split("\n");
							for(String line : outputLines) {
								logFile.write(line + "\n");
							}
							logFile.close();
						}catch(IOException exception) {
							exception.printStackTrace();
						}
						if(mainPool != null && !mainPool.isShutdown() && !mainPool.isTerminated()) {
							mainPool.shutdownNow();
						}
						for(Process p : mainProcesses) {
							if(p.isAlive()) {
								p.destroyForcibly();
							}
						}
						Platform.exit();
						System.exit(0);
					}else {
						e.consume();
					}
				}else {
					File qaFile = new File(outDir.getAbsolutePath() + sep + "QAlog.txt");
					if(!qaFile.exists()) {
						Methods.makeQALog(qaFile, Dispatcher.version, outDir, inputFile, primerFile, BBToolsLocation, BLASTLocation);
					}
					try {
						FileWriter logFile = new FileWriter(outDir.getAbsolutePath() + sep + "log.txt", true);
						String[] outputLines = outputField.getText().split("\n");
						for(String line : outputLines) {
							logFile.write(line + "\n");
						}
						logFile.close();
					}catch(IOException exception) {
						exception.printStackTrace();
					}
				}
			}
		});
		primaryStage.setTitle("InSilico PCR " + Dispatcher.version);
		primaryStage.show();
	}
	
	// Makes directories within the output directory
	public void makeDirectories() {
		detailedDir = new File(outDir.getAbsolutePath() + sep + "detailed_report");
		detailedDir.mkdirs();
		consolidatedDir = new File(outDir.getAbsolutePath() + sep + "consolidated_report");
		consolidatedDir.mkdirs();
	}
	
	// Find dependencies
	public void findDependencies() {
		String codeLocation = Dispatcher.class.getProtectionDomain().getCodeSource().getLocation().getPath();
		String codeParent = codeLocation;
		try{
			codeParent = (new File(codeLocation)).getCanonicalPath();
		}catch(IOException e) {
			Methods.logMessage(outputField, e.getStackTrace().toString());
		}
		Path dir = Paths.get(codeParent);
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
		
		Find.Finder finder2;
		if(System.getProperties().getProperty("os.name").contains("Windows")) {
			finder2 = new Find.Finder("**makeblastdb.exe", dir);
		}else {
			finder2 = new Find.Finder("**makeblastdb", dir);
		}
		for(Path path : finder2.run()) {
			File blastdbpath = path.toFile();
			BLASTLocation = blastdbpath.getParentFile();
		}
		if(BBToolsLocation == null || BLASTLocation == null) {
			Methods.logMessage(outputField, "BBToolsLocation or BLASTLocation is null");
		}
		
		Find.Finder finder3;
		if(System.getProperties().getProperty("os.name").contains("Windows")) {
			finder3 = new Find.Finder("**InSilicoPCR_windows_java_runtime", dir);
		}else {
			finder3 = new Find.Finder("**InSilicoPCR_linux_java_runtime", dir);
		}
		for(Path path : finder3.run()) {
			File javapath = path.toFile();
			if(javapath.isDirectory()) {
				for(File item : javapath.listFiles()) {
					if(item.isDirectory()) {
						for(File item2 : item.listFiles()) {
							if(System.getProperties().getProperty("os.name").contains("Windows")) {
								if(item2.getName().equals("java.exe")) {
									JavaLocation = item.getAbsoluteFile();
								}
							}else {
								if(item2.getName().equals("java")) {
									JavaLocation = item.getAbsoluteFile();
								}
							}
						}
					}
				}
			}
		}
		if(System.getProperties().getProperty("os.name").contains("Windows")) {
			javaCall = JavaLocation.getAbsolutePath() + sep + "java.exe";
		}else {
			javaCall = JavaLocation.getAbsolutePath() + sep + "java";
		}
	}
	
	// Main body of the pipeline, runs the contained methods in order
	public class RunPCRTask extends Task<Void> {
		
		private TextArea outputField;
		private GridPane pane;
		
		public RunPCRTask(TextArea outputField, GridPane pane) {
			this.outputField = outputField;
			this.pane = pane;
		}
		
		public Void call() {
			
			currentlyRunning = true;
			
			long startTime = System.nanoTime();
			
			Methods.logMessage(outputField, "Beginning Program Run");
			findDependencies();
			Methods.logMessage(outputField, "Found Dependencies");
			updateProgress(1, 13);
			makeDirectories();
			Methods.logMessage(outputField, "Created Directories");
			updateProgress(2, 13);
			sampleDict = Methods.createSampleDict(inputFile);
			Methods.logMessage(outputField, "Created Sample Dictionary");
			updateProgress(3, 13);
			primerDict = Methods.parseFastaToDictionary(primerFile);
			Methods.logMessage(outputField, "Created Primer Dictionary");
			updateProgress(4, 13);
			Methods.processPrimers(primerDict, outputField, outDir, sep);
			Methods.logMessage(outputField, "Finished Formatting Primers");
			updateProgress(5, 13);
			// Check if any fastq files are present
			for(String key : sampleDict.keySet()) {
				if(sampleDict.get(key).getFileType().equals("fastq")) {
					Methods.logMessage(outputField, "FastQ files identified, conducting baiting and assembly");
					fastqPresent = true;
					break;
				}
			}
			if(fastqPresent) {
				runBaitTask(outputField);
				Methods.logMessage(outputField, "Completed First Baiting");
				updateProgress(6, 13);
				runSecondBaitTask(outputField);
				Methods.logMessage(outputField, "Completed Second Baiting");
				updateProgress(7, 13);
				runAssembleTask(outputField);
				Methods.logMessage(outputField, "Completed Assembly");
				updateProgress(8, 13);
			}
			if(!System.getProperty("os.name").contains("Windows")) {
				Methods.makeExecutable(BLASTLocation);
			}
			Methods.makeBlastDB(new File(outDir.getAbsolutePath() + sep + "primer_tmp.fasta"), BLASTLocation, outputField);
			Methods.logMessage(outputField, "Completed Database Creation");
			updateProgress(9, 13);
			// If files were fastq, need to use the assembly file instead of raw files
//			runBLASTTask(outputField);
			runBLASTTask task = new runBLASTTask();
			BLASTProgress.progressProperty().bind(task.progressProperty());
			Thread t = new Thread(task);
			t.start();
			try {
				t.join();
			}catch(InterruptedException e) {
				e.printStackTrace();
			}
			Methods.logMessage(outputField, "Completed BLAST");
			updateProgress(10, 13);
			Methods.addContigDict(sampleDict);
			Methods.logMessage(outputField, "Completed Contig Dictionary");
			Methods.parseBlastOutput(consolidatedDir, detailedDir, primerDict, mismatches, sampleDict);
			Methods.logMessage(outputField, "Parsed BLAST output");
			updateProgress(11, 13);
			Methods.makeConsolidatedReport(consolidatedDir, sep, sampleDict, primerDict);
			Methods.logMessage(outputField, "Created Consolidated Report");
			updateProgress(12, 13);
			Methods.makeQALog(new File(outDir.getAbsolutePath() + sep + "QAlog.txt"), Dispatcher.version, outDir, inputFile, primerFile, BBToolsLocation, BLASTLocation);
			
			long endTime = System.nanoTime();
			
			Methods.logMessage(outputField, "Done in " + Long.toString((endTime - startTime)/1000000000) + " seconds");
			updateProgress(13, 13);
			currentlyRunning = false;
			pane.add(gelButton, 30, 45, 7, 3);
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
	
	public class runBLASTTask extends Task<Void> {
		
		public runBLASTTask() {
		}
		
		public Void call() {
			mainPool = new ThreadPoolExecutor(threads, Integer.MAX_VALUE, Long.MAX_VALUE, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
			for(String key : sampleDict.keySet()) {
				if(sampleDict.get(key).getFileType().equals("fastq")) {
					BlastTask task = new BlastTask(outDir.getAbsolutePath() + sep + "primer_tmp.fasta", sampleDict.get(key).getAssemblyFile(),
							detailedDir, sep, BLASTLocation);
					mainPool.submit(task);
				}else {
					for(String file : sampleDict.get(key).getFiles()) {
						BlastTask task = new BlastTask(outDir.getAbsolutePath() + sep + "primer_tmp.fasta", file,
								detailedDir, sep, BLASTLocation);
						mainPool.submit(task);
					}
				}
			}
			try {
				mainPool.shutdown();
				while(!mainPool.getQueue().isEmpty()) {
					updateProgress(mainPool.getCompletedTaskCount() + threads, mainPool.getTaskCount());
				}
				mainPool.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
			}catch(InterruptedException e) {
				e.printStackTrace();
			}
			updateProgress(1, 1);
			return null;
		}
	}
	
	// Bait FastQ reads from input files using BBDuk and the primer file as the target
	public class BaitTask extends Task<Void> {
		
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
				if(sampleDict.get(key).getFileType().equals("fastq")) {
					BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();
					consumer = new MessageConsumer(messageQueue, this.outputField);
					
					Sample currentSample = sampleDict.get(key);
					File sampleDir = new File(detailedDir.getAbsolutePath() + sep + currentSample.getName());
					sampleDir.mkdirs();
					String[] fullProcessCall;
					if(currentSample.getFiles().size() == 2) {
						fullProcessCall = new String[] {javaCall, "-ea", "-Xmx7g", "-cp", "./current", "jgi.BBDuk", "ref=" + ref, "k=" + klength,
								"in1=" + currentSample.getFiles().get(0), "in2=" + currentSample.getFiles().get(1), "hdist=" + mismatches,
								"threads=" + threads, "interleaved=t", "outm=" + sampleDir.getAbsolutePath() + sep + currentSample.getName() +
								"_targetMatches.fastq.gz"};
					}else {
						fullProcessCall = new String[] {javaCall, "-ea", "-Xmx7g", "-cp", "./current", "jgi.BBDuk", "ref=" + ref, "k=" + klength,
								"in=" + currentSample.getFiles().get(0), "hdist=" + mismatches, "threads=" + threads, "interleaved=t",
								"outm=" + sampleDir.getAbsolutePath() + sep + currentSample.getName() + "_targetMatches.fastq.gz"};
					}
					try {
						Process p = new ProcessBuilder(fullProcessCall).directory(BBToolsLocation).start();
						BufferedReader stdout = new BufferedReader(new InputStreamReader(p.getErrorStream()));
						Platform.runLater(() -> consumer.start());
						mainProcesses.add(p);
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
			}
			mainProcesses.clear();
			return null;
		}
	}
	
	// Bait more FastQ read nearby the originally baited reads using the originally baited reads as bait themselves
	// If USERS find issues with memory overflow, can use qhdist instead of hdist. Sacrifices speed for memory by 
	// Conducting mutations on query instead of reference? Dramatically reduces memory usage. 
	public class SecondBaitTask extends Task<Void> {
		
		private TextArea outputField;
		private String line;
		private MessageConsumer consumer;
		
		public SecondBaitTask(TextArea outputField) {
			this.outputField = outputField;
		}
		
		public Void call() {
			for(String key : sampleDict.keySet()) {
				if(sampleDict.get(key).getFileType().equals("fastq")) {
					BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();
					consumer = new MessageConsumer(messageQueue, this.outputField);
					
					Sample currentSample = sampleDict.get(key);
					File sampleDir = new File(detailedDir.getAbsolutePath() + sep + currentSample.getName());
					String ref = sampleDir.getAbsolutePath() + sep + currentSample.getName() + "_targetMatches.fastq.gz";
					String[] fullProcessCall;
					if(currentSample.getFiles().size() == 2) {
						fullProcessCall = new String[] {javaCall, "-ea", "-Xmx7g", "-cp", "./current", "jgi.BBDuk", "ref=" + ref, 
								"in1=" + currentSample.getFiles().get(0), "in2=" + currentSample.getFiles().get(1), "hdist=" + mismatches,
								"threads=" + threads, "interleaved=t", "outm=" + sampleDir.getAbsolutePath() + sep + currentSample.getName() +
								"_doubleTargetMatches.fastq.gz"};
					}else {
						fullProcessCall = new String[] {javaCall, "-ea", "-Xmx7g", "-cp", "./current", "jgi.BBDuk", "ref=" + ref,
								"in=" + currentSample.getFiles().get(0), "hdist=" + mismatches, "threads=" + threads, "interleaved=t",
								"outm=" + sampleDir.getAbsolutePath() + sep + currentSample.getName() + "_doubleTargetMatches.fastq.gz"};
					}
					try {
						Process p = new ProcessBuilder(fullProcessCall).directory(BBToolsLocation).start();
						BufferedReader stdout = new BufferedReader(new InputStreamReader(p.getErrorStream()));
						Platform.runLater(() -> consumer.start());
						mainProcesses.add(p);
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
			}
			mainProcesses.clear();
			return null;
		}
	}
	
	// Assemble reads from both rounds of baiting to attempt to get long enough contigs to ensure as many primer hits are contained on the same contigs as possible
	public class AssembleTask extends Task<Void> {
		
		private TextArea outputField;
		private String line;
		private MessageConsumer consumer;
		
		public AssembleTask(TextArea outputField) {
			this.outputField = outputField;
		}
		
		public Void call() {
			for(String key : sampleDict.keySet()) {
				if(sampleDict.get(key).getFileType().equals("fastq")) {
					BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();
					consumer = new MessageConsumer(messageQueue, this.outputField);
					
					Sample currentSample = sampleDict.get(key);
					File sampleDir = new File(detailedDir.getAbsolutePath() + sep + currentSample.getName());
					
					String in = sampleDir.getAbsolutePath() + sep + currentSample.getName() + "_doubleTargetMatches.fastq.gz";
					String out = sampleDir.getAbsolutePath() + sep + currentSample.getName() + "_assembly.fasta";
					
					// Make sure that the sample contains a reference to its own assembly file
					currentSample.setAssemblyFile(out);
					
					String[] fullProcessCall = {"java", "-ea", "-Xmx7g", "-cp", "./current", "assemble.Tadpole", 
							"in=" + in, "out=" + out, "threads=" + threads};
				
					try {
						Process p = new ProcessBuilder(fullProcessCall).directory(BBToolsLocation).start();
						BufferedReader reader = new BufferedReader(new InputStreamReader(p.getErrorStream()));
						Platform.runLater(() -> consumer.start());
						mainProcesses.add(p);
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
			}
			mainProcesses.clear();
			return null;
		}
	}
	
	// Run Blast on the provided primers and query, calls addHeaderToTSV on the resulting .tsv file
	public class BlastTask extends Task<Void> {
		
		private String primers;
		private String query;
		private File detailedDir;
		private String sep;
		private File BLASTLocation;
		private MessageConsumer consumer;
		private String line;
		
		public BlastTask(String primers, String query, File detailedDir, String sep, File BLASTLocation) {
			this.primers = primers;
			this.query = query;
			this.detailedDir = detailedDir;
			this.sep = sep;
			this.BLASTLocation = BLASTLocation;
		}
		
		public Void call() {
			BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();
			consumer = new MessageConsumer(messageQueue, outputField);
			
			File file = new File(query);
			String name = file.getName().split("_assembly\\.fasta")[0];
			name = name.split("\\.fasta")[0];
			name = name.split("\\.fna")[0];
			name = name.split("\\.ffn")[0];
			File blastOutput = new File(detailedDir.getAbsolutePath() + sep + name);
			blastOutput.mkdirs();
			File blastTSV = new File(blastOutput.getAbsolutePath() + sep + name + ".tsv");
			String[] windowsFullProcessCall = {BLASTLocation.getAbsolutePath() + sep + "blastn.exe", "-task", "blastn-short", "-query",
					query, "-db", primers, "-evalue", "1e-1", "-num_alignments", "1000000", "-num_threads", "1", "-outfmt", 
					"6 qseqid sseqid positive mismatch gaps evalue bitscore slen length qstart qend qseq sstart send sseq",
					"-out", blastTSV.getAbsolutePath()};
			String[] linuxFullProcessCall = {BLASTLocation.getAbsolutePath() + sep + "blastn", "-task", "blastn-short", "-query",
					query, "-db", primers, "-evalue", "1e-1", "-num_alignments", "1000000", "-num_threads", "1", "-outfmt", 
					"6 qseqid sseqid positive mismatch gaps evalue bitscore slen length qstart qend qseq sstart send sseq",
					"-out", blastTSV.getAbsolutePath()};
			try {
				Process p;
				if(System.getProperty("os.name").contains("Windows")) {
					p = new ProcessBuilder(windowsFullProcessCall).start();
				}else {
					p = new ProcessBuilder(linuxFullProcessCall).start();
				}
				BufferedReader reader = new BufferedReader(new InputStreamReader(p.getErrorStream()));
				Platform.runLater(() -> consumer.start());
				mainProcesses.add(p);
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
			mainProcesses.clear();
			Methods.addHeaderToTSV(blastTSV);
			return null;
		}
	}
	
	public static void displayGelImage() {
		
	}
	
	public static void main(String[] args) {
		Application.launch(MainRun.class);
	}

}
