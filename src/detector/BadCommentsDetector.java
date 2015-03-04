package detector;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.bcel.classfile.Method;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.SourceLineAnnotation;
import edu.umd.cs.findbugs.ba.AnalysisContext;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.ba.SourceFile;
import edu.umd.cs.findbugs.ba.SourceFinder;

public class BadCommentsDetector extends BytecodeScanningDetector {

	
	private final BugReporter bugReporter;
	private boolean srcInited;
	private String[] sourceLines;
	
	public BadCommentsDetector(final BugReporter bugReporter) {
		this.bugReporter = bugReporter;
	}
	
	/**
	 * overrides the visitor to initialize the 'has source' flag
	 *
	 * @param classContext the context object for the currently parsed class
	 */
	@Override
	public void visitClassContext(ClassContext classContext) {
		srcInited = false;
		super.visitClassContext(classContext);
	}
	
	@Override
	public void visitMethod(final Method obj) {
		sourceLines = getSourceLines(obj);
		if (sourceLines == null) {
			out.println("Source was null");
			return;
		}
		
		SourceLineAnnotation methodAnnotation = SourceLineAnnotation.forEntireMethod(getClassContext().getJavaClass(), obj);
		
		boolean hasComment = false;
		int nonEmptyLines = 0;
		if (methodAnnotation.getStartLine() < 0 || methodAnnotation.getEndLine() < 0) {
			return;
		}
		for (int i = methodAnnotation.getStartLine() - 1; i <= methodAnnotation.getEndLine();i++) {
			if (sourceLines[i].indexOf("//") >= 0) {
				hasComment = true;
			}
			if (sourceLines[i].matches("\\s*\\S.*")) {
				nonEmptyLines++;
			}
		}
		
		if (!hasComment && nonEmptyLines > 2) {
			int priority = LOW_PRIORITY;
			if (nonEmptyLines > 10) {
				priority = NORMAL_PRIORITY;
			}
			if (nonEmptyLines > 25) {
				priority = HIGH_PRIORITY;
			}
			bugReporter.reportBug(new BugInstance(this, "BAD_COMMENTS", priority)
			.addClass(this)
			.addMethod(this));
		}
		
	}
	
	/**
	 * reads the sourcefile based on the source line annotation for the method
	 *
	 * @param obj the method object for the currently parsed method
	 *
	 * @return an array of source lines for the method
	 */
	private String[] getSourceLines(Method obj) {

		BufferedReader sourceReader = null;

		if (srcInited)
			return sourceLines;

		try {
			SourceLineAnnotation srcLineAnnotation = SourceLineAnnotation.forEntireMethod(getClassContext().getJavaClass(), obj);
			out.println("At "+new File(".").getAbsolutePath());
			out.println("source line annotation "+srcLineAnnotation);
			out.println(srcLineAnnotation.getSourcePath());
			if (srcLineAnnotation != null)
			{
				sourceReader = findSourceReader(srcLineAnnotation);

				List<String> lines = new ArrayList<String>(100);
				String line;
				while ((line = sourceReader.readLine()) != null)
					lines.add(line);
				sourceLines = lines.toArray(new String[lines.size()]);
			}
		} catch (IOException ioe) {
			ioe.printStackTrace(out);
		}
		finally {
			try {
				if (sourceReader != null)
					sourceReader.close();
			} catch (IOException ioe2) {
				//noop
			}
		}
		srcInited = true;
		return sourceLines;
	}

	private BufferedReader findSourceReader(SourceLineAnnotation srcLineAnnotation) throws IOException {
		
		SourceFinder sourceFinder = AnalysisContext.currentAnalysisContext().getSourceFinder();
		try {
			SourceFile sourceFile = sourceFinder.findSourceFile(srcLineAnnotation.getPackageName(), srcLineAnnotation.getSourceFile());
			return new BufferedReader(new InputStreamReader(sourceFile.getInputStream(), StandardCharsets.UTF_8));
			
		} catch (IOException e) {
			out.println("Can't find source via normal methods.  Trying maven approach");
		}
		
		File currentDir = new File(".").getAbsoluteFile();
		
		BufferedReader reader = findSrcRecursive(currentDir, srcLineAnnotation);
		if (reader != null)
			return reader;
		throw new IOException("Could not find source for "+srcLineAnnotation);
	}
	
	private BufferedReader findSrcRecursive(File currentDir, SourceLineAnnotation sla) throws IOException {
		if (currentDir == null) {
			return null;
		}
		out.println("In "+currentDir.getAbsolutePath());
		String[] files = currentDir.list();
		if (files == null || files.length == 0) {
			return null;
		}
		// look for source
		for(String f: files) {
			out.println(f);
			if ("src".equals(f)) {
				return searchSrcForSLA(new File(currentDir, f), sla);
			}
		}
		// no source? do it recursively.
		
		for(String f: files) {
			if (f.startsWith(".")) {		//ignore hidden
				continue;
			}
			BufferedReader retVal = findSrcRecursive(new File(currentDir, f), sla);
			if (retVal != null) {
				return retVal;
			}
		}
		return null;
	}
	
	private File findFolderWithName(File currentDir, String name) {
		String[] files = currentDir.list();
		if (files == null || files.length == 0) {
			return null;
		}
		for(String f: files) {
			if (name.equals(f)) {
				return new File(currentDir, f);
			}
		}
		return null;
	}

	private BufferedReader searchSrcForSLA(File currentDir, SourceLineAnnotation sla) throws IOException {
		out.println("found src folder... looking in "+currentDir.getAbsolutePath());
		currentDir = findFolderWithName(currentDir, "main");
		if (currentDir == null) {
			return null;
		}
		
		currentDir = findFolderWithName(currentDir, "java");
		if (currentDir == null) {
			return null;
		}
		
		File finalFile = new File(currentDir, sla.getSourcePath());
		out.println("Looking at final file "+finalFile.getAbsolutePath());
		
		if (finalFile.exists()) {
			return new BufferedReader(new InputStreamReader(new FileInputStream(finalFile), StandardCharsets.UTF_8));
		}
		
		return null;
	}




	private static PrintStream out;

	static {
		try {
			out = new PrintStream(new FileOutputStream("/tmp/findbugsConsole.txt"),true, StandardCharsets.UTF_8.name());
			out.println("Hello source code checker");
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}
}
