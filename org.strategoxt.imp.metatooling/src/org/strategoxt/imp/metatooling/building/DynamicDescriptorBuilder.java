package org.strategoxt.imp.metatooling.building;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Path;
import org.spoofax.interpreter.core.Interpreter;
import org.spoofax.interpreter.core.InterpreterException;
import org.spoofax.interpreter.terms.IStrategoString;
import org.strategoxt.imp.metatooling.loading.DynamicDescriptorUpdater;
import org.strategoxt.imp.runtime.Debug;
import org.strategoxt.imp.runtime.Environment;

/**
 * @author Lennart Kats <lennart add lclnet.nl>
 */
public class DynamicDescriptorBuilder {
	
	private final Map<String, Set<IResource>> mainEditorFiles =
		new HashMap<String, Set<IResource>>();
	
	private final Map<IResource, Set<String>> includedEditorFiles =
		new HashMap<IResource, Set<String>>();
	
	private final Interpreter builder;
	
	private final DynamicDescriptorUpdater loader;
	
	public DynamicDescriptorBuilder(DynamicDescriptorUpdater loader) {
		try {
			this.loader = loader;
			
			Debug.startTimer("Loading dynamic editor builder");

			builder = Environment.createInterpreter();
			builder.setIOAgent(new TrackingIOAgent());
			builder.load(DynamicDescriptorBuilder.class.getResourceAsStream("/include/sdf2imp.ctree"));
			
			Debug.stopTimer("Successfully loaded dynamic editor builder");
			
		} catch (Throwable e) {
			throw new RuntimeException("Unable to initialize dynamic builder", e);
		}
	}
	
	public void updateResource(IResource resource) {
		String filename = resource.getFullPath().toPortableString();
		
		try {
			Set<IResource> mainFiles = mainEditorFiles.get(filename);
			if (mainFiles != null) {
				for (IResource mainFile : mainFiles)
					buildDescriptor(mainFile);
			}
			
			if (isMainFile(filename))
				buildDescriptor(resource);
		
		} catch (RuntimeException e) {
			Environment.logException("Unable to build descriptor for " + filename);
		} catch (Error e) { // workspace thread swallows this >:(
			Environment.logException("Unable to build descriptor for " + filename);
		}
	}

	private void buildDescriptor(IResource mainFile) {
		try {
			builder.setCurrent(builder.getFactory().makeString(mainFile.getFullPath().toOSString()));
			builder.invoke("dr-scope-all-start");
			boolean success = builder.invoke("sdf2imp-for-file");
			builder.invoke("dr-scope-all-end");
			
			if (!success) {
				Environment.logStrategyFailure("Unable to build descriptor for " + mainFile, builder);
				return;
			}
			
			updateDependencies(mainFile);
			
		} catch (InterpreterException e) {
			Environment.logException("Unable to build descriptor for " + mainFile, e);
		}
		
		String result = ((IStrategoString) builder.current()).stringValue();
		IResource packedDescriptor = mainFile.getParent().getFile(Path.fromOSString(result));
		loader.loadPackedDescriptor(packedDescriptor);
	}

	private void updateDependencies(IResource mainFile) {
		TrackingIOAgent agent = (TrackingIOAgent) builder.getIOAgent();
		
		// Remove old dependencies		
		for (String oldDependency : includedEditorFiles.get(mainFile)) {
			Set<IResource> set = mainEditorFiles.get(oldDependency);
			if (set != null) set.remove(mainFile);
		}
		
		// Add new dependencies
		includedEditorFiles.put(mainFile, agent.getTracked());		
		for (String newDependency : agent.getTracked()) {
			newDependency = new Path(newDependency).toPortableString();
			Set<IResource> set = mainEditorFiles.get(newDependency);
			if (set == null) {
				set = new HashSet<IResource>();
				set.add(mainFile);
				mainEditorFiles.put(newDependency, set);
			} else {
				set.add(mainFile);
			}
		}
		
		agent.setTracked(new HashSet<String>());
	}
	
	private static boolean isMainFile(String file) {
		// TODO: Determine if a file is the main descriptor file by its contents?
		// InputStream stream = builder.getIOAgent().openInputStream(file);
		
		return file.matches(".*(-Main|\\.main)\\.esv");
	}
}