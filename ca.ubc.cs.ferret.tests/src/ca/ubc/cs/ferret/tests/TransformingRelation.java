package ca.ubc.cs.ferret.tests;

import java.util.Collection;
import java.util.Iterator;

import org.apache.commons.collections15.Transformer;
import org.apache.commons.collections15.iterators.ArrayIterator;

import ca.ubc.cs.ferret.model.AbstractToolRelation;
import ca.ubc.cs.ferret.types.ConversionResult;
import ca.ubc.cs.ferret.types.FerretObject;
import ca.ubc.cs.ferret.types.ConversionSpecification.Fidelity;

public class TransformingRelation<T> extends AbstractToolRelation {
	public enum ItemTreatment {
		AllMustBeConformant, DiscardNonConformant, PassThroughNonConformant
	};

	protected Class<T> clazz;
	protected Transformer<T,?> transformer;
	protected Iterator<FerretObject> iterator;
	protected ItemTreatment argumentTreatment = ItemTreatment.PassThroughNonConformant;
	
	public TransformingRelation(Class<T> c, Transformer<T,?> t) {
		clazz = c;
		transformer = t;
	}
	

	@SuppressWarnings("unchecked")
	@Override
	protected boolean configure(FerretObject... arguments) {
		if(argumentTreatment == ItemTreatment.AllMustBeConformant) {
			for(FerretObject arg : arguments) {
				ConversionResult<?> result = arg.convert(clazz, 1, Fidelity.Approximate);
				if(result == null || result.getResults().isEmpty()) {
					return false;
				}
			}
		}
		iterator = new ArrayIterator<FerretObject>(arguments);
		return true;
	}


	public boolean hasNext() {
		return iterator.hasNext();
	}

	public FerretObject next() {
		FerretObject fo = iterator.next();
		ConversionResult<T> result = fo.convert(clazz, 1, Fidelity.Approximate);
		if(result == null) {
			return argumentTreatment == ItemTreatment.PassThroughNonConformant
					? fo : null;
		}
		return new FerretObject(transformer.transform(result.getSingleResult()), result.getFidelity(),
				fo.getSphere());
	}


	public ItemTreatment getArgumentTreatment() {
		return argumentTreatment;
	}


	public void setArgumentTreatment(ItemTreatment argumentTreatment) {
		this.argumentTreatment = argumentTreatment;
	}
}