package org.strategoxt.imp.runtime.stratego.adapter;

import lpg.runtime.IAst;

import org.spoofax.NotImplementedException;
import org.spoofax.interpreter.terms.IStrategoList;
import org.spoofax.interpreter.terms.IStrategoTerm;
import org.spoofax.interpreter.terms.ITermPrinter;
import org.strategoxt.imp.runtime.Environment;

/**
 * @author Lennart Kats <lennart add lclnet.nl>
 * @author Karl Trygve Kalleberg <karltk add strategoxt.org>
 */
public class WrappedAstNodeList extends WrappedAstNode implements IStrategoList {
	
	private final int offset;
	
	protected WrappedAstNodeList(WrappedAstNodeFactory factory, IAst node) {
		this(factory, node, 0);
	}

	protected WrappedAstNodeList(WrappedAstNodeFactory factory, IAst node, int offset) {
		super(factory, node);
		this.offset = offset;
	}

	@Override
	protected boolean slowCompare(IStrategoTerm second) {
		if (second.getTermType() != IStrategoTerm.LIST)
			return false;

		IStrategoList snd = (IStrategoList) second;
		if (size() != snd.size())
			return false;
		if (!getAnnotations().match(second.getAnnotations()))
			return false;
		for (int i = 0, sz = size(); i < sz; i++) {
			if (!get(i).match(snd.get(i)))
				return false;
		}
		return true;
	}

	@Override
	public final IStrategoTerm get(int index) {
		return getSubterm(index);
	}
	
	@Override
	public IStrategoTerm getSubterm(int index) {
		return super.getSubterm(index + offset);
	}

	@Override
	public IStrategoTerm head() {
		return get(offset);
	}

	@Override
	public final boolean isEmpty() {
		return getSubtermCount() == 0;
	}

	@Override
	public final int size() {
		return getSubtermCount();
	}

	@Override
	public IStrategoList tail() {
		// TODO Auto-generated method stub
		throw new NotImplementedException();
	}

	@Override
	public int getTermType() {
		return LIST;
	}

	@Override
	public void prettyPrint(ITermPrinter pp) {
		int sz = size();
		if (sz > 0) {
			pp.println("[");
			pp.indent(2);
			get(0).prettyPrint(pp);
			for (int i = 1; i < sz; i++) {
				pp.print(",");
				pp.nextIndentOff();
				get(i).prettyPrint(pp);
				pp.println("");
			}
			pp.println("");
			pp.print("]");
			pp.outdent(2);

		} else {
			pp.print("[]");
		}
		printAnnotations(pp);
	}

	@Override
	public int hashCode() {
		long hc = 4787;
		for (int i = 0; i < getSubtermCount(); i++) {
			hc *= getSubterm(i).hashCode();
		}
		return (int) (hc >> 2);
	}

	@Override
	@Deprecated
	public IStrategoList prepend(IStrategoTerm prefix) {
		return Environment.getTermFactory().makeList(prefix, this);
	}

}
