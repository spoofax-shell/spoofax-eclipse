package org.strategoxt.imp.runtime.parser;

import static java.lang.Math.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import lpg.runtime.IToken;
import lpg.runtime.PrsStream;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.imp.language.Language;
import org.eclipse.imp.language.LanguageRegistry;
import org.eclipse.imp.model.ISourceProject;
import org.eclipse.imp.parser.IMessageHandler;
import org.eclipse.imp.parser.IParseController;
import org.eclipse.imp.parser.ISourcePositionLocator;
import org.eclipse.imp.parser.SimpleAnnotationTypeInfo;
import org.eclipse.imp.services.IAnnotationTypeInfo;
import org.eclipse.imp.services.ILanguageSyntaxProperties;
import org.eclipse.jface.text.IRegion;
import org.spoofax.jsglr.BadTokenException;
import org.spoofax.jsglr.SGLR;
import org.spoofax.jsglr.SGLRException;
import org.spoofax.jsglr.TokenExpectedException;
import org.strategoxt.imp.runtime.Environment;
import org.strategoxt.imp.runtime.dynamicloading.BadDescriptorException;
import org.strategoxt.imp.runtime.parser.ast.AstNode;
import org.strategoxt.imp.runtime.parser.ast.AstNodeLocator;
import org.strategoxt.imp.runtime.parser.tokens.SGLRToken;
import org.strategoxt.imp.runtime.parser.tokens.SGLRTokenIterator;
import org.strategoxt.imp.runtime.parser.tokens.TokenKind;
import org.strategoxt.imp.runtime.parser.tokens.TokenKindManager;
import org.strategoxt.imp.runtime.services.StrategoFeedback;

/**
 * IMP parse controller for an SGLR parser.
 *
 * @author Lennart Kats <L.C.L.Kats add tudelft.nl>
 * @author Karl Trygve Kalleberg <karltk add strategoxt.org>
 */
public class SGLRParseController implements IParseController {
	@Deprecated
	private final List<String> problemMarkerTypes = new ArrayList<String>();
	
	private final TokenKindManager tokenManager = new TokenKindManager();
	
	private final JSGLRI parser;
	
	private final Language language;
	
	private final ILanguageSyntaxProperties syntaxProperties;
	
	private AstNode currentAst;
	
	private ISourceProject project;
	
	private IPath path;
	
	private IMessageHandler messages;

	// Simple accessors
	
	public final AstNode getCurrentAst() { 
		return currentAst;
	}

	public final ISourceProject getProject() {
		return project;
	}

	/**
	 * @return either a project-relative path, if getProject() is non-null, or
	 *         an absolute path.
	 */
    public final IPath getPath() {
    	return project == null
			? path
			: project.getRawProject().getLocation().append(path);
    }
	
	// Parsing and initialization
    
    static {
    	SGLR.setWorkAroundMultipleLookahead(true);
    }
    
    /**
     * Create a new SGLRParseController.
     * 
     * @param language      The name of the language, as registered in the {@link LanguageRegistry}.
     * @param startSymbol	The start symbol of this grammar, or null.
     */
    public SGLRParseController(Language language, ILanguageSyntaxProperties syntaxProperties,
			String startSymbol) {
    	
    	this.language = language;
    	this.syntaxProperties = syntaxProperties;
    	
    	parser = new JSGLRI(Environment.getParseTable(language), startSymbol, this, tokenManager);
    }

    public void initialize(IPath filePath, ISourceProject project,
    		IMessageHandler handler) {
		this.path = filePath;
		this.project = project;
		this.messages = handler;
    }

	public AstNode parse(String input, boolean scanOnly, IProgressMonitor monitor) {
		if (getPath() == null)
		    throw new IllegalStateException("SGLR parse controller not initialized");

		try {
			messages.clearMessages();

			// TODO2: Optimization - don't produce AST if scanOnly is true
			currentAst = null;
			currentAst = parser.parse(input.toCharArray(), getPath().toPortableString());
		} catch (TokenExpectedException e) {
			reportParseError(e);
		} catch (BadTokenException e) {
			reportParseError(e);
		} catch (SGLRException e) {
			reportParseError(e);
		} catch (IOException e) {
			reportParseError(e);
		} catch (RuntimeException e) {
			Environment.logException("Unexpected error during parsing", e);
			reportParseError(e);
		}
		
		// HACK: Need to call IModelListener.update manually, the IMP extension point is not implemented?
		try {
			StrategoFeedback feedback = Environment.getDescriptor(getLanguage()).getStrategoFeedback();
			if (feedback != null) feedback.asyncUpdate(this, null);
		} catch (BadDescriptorException e) {
			Environment.logException("Unexpected error during analysis", e);
			reportParseError(e);
		} catch (RuntimeException e) {
			Environment.logException("Unexpected exception during analysis", e);
			reportParseError(e);
		}

		return currentAst;
	}
	
	// Language information

	public Language getLanguage() {
		return language;
	}
	
	public ISourcePositionLocator getNodeLocator() {
		return new AstNodeLocator();
	}
	
	public ILanguageSyntaxProperties getSyntaxProperties() {
		return syntaxProperties;
	}

	public IAnnotationTypeInfo getAnnotationTypeInfo() {
		return new SimpleAnnotationTypeInfo();
	}

	public Iterator<IToken> getTokenIterator(IRegion region) {
		// TODO: Return a damaged token stream on parse errors
		PrsStream stream = parser.getParseStream();
		if (stream.getTokens().size() == 0 || getCurrentAst() == null) {
			// Parse hasn't succeeded yet, consider the entire stream as one big token
			stream.addToken(new SGLRToken(stream, region.getOffset(), stream.getStreamLength() - 1,
					TokenKind.TK_UNKNOWN.ordinal()));
		}
		return new SGLRTokenIterator(stream, region);
	}
	
	// Problem markers
	
	@Deprecated
	public final List<String> getProblemMarkerTypes() {
		return problemMarkerTypes;
	}
	
	@Deprecated
	public void addProblemMarkerType(String problemMarkerType) {
		problemMarkerTypes.add(problemMarkerType);
	}
	
	@Deprecated
	public void removeProblemMarkerType(String problemMarkerType) {
		problemMarkerTypes.remove(problemMarkerType);
	}
	
	// Error reporting
	
	@Deprecated
	public final IMessageHandler getMessages() {
		return messages;
	}
	
	protected void reportParseError(TokenExpectedException exception) {
		String message = exception.getShortMessage();
		IToken token = parser.getTokenizer().makeErrorToken(exception.getOffset());
		
		reportTokenError(token, message);
	}
	
	protected void reportParseError(BadTokenException exception) {
		IToken token = parser.getTokenizer().makeErrorToken(exception.getOffset());
		String message = exception.isEOFToken()
        	? exception.getShortMessage()
        	: "'" + token.toString() + "' not expected here";

        reportTokenError(token, message);
	}
	
	protected void reportParseError(Exception exception) {
		String message = "Internal parsing error: " + exception;
		exception.printStackTrace();
		
		IToken token = parser.getTokenizer().makeErrorToken(0);
		
		reportTokenError(token, message);
	}
	
	private void reportTokenError(IToken token, String message) {
		messages.handleSimpleMessage(
				message, max(0, token.getStartOffset()), max(0, token.getEndOffset()),
				token.getColumn(), token.getEndColumn(), token.getLine(), token.getEndLine());
		// UNDONE: Using AstMessageHandler
		// parseErrors.addMarker(getProject().getRawProject().getFile(path), token, token, message, IMarker.SEVERITY_ERROR);
	}
}
