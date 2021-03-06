package ca.ubc.cs.ferret.model;

import org.eclipse.core.runtime.IProgressMonitor;

import ca.ubc.cs.ferret.types.FerretObject;

public abstract class FunctionalSphereCompositor extends AbstractSphereCompositor {

	public FunctionalSphereCompositor() {
	}

	public FunctionalSphereCompositor(ISphere... spheres) {
		super(spheres);
	}


	@Override
	public AbstractRelationResolvingState createResolverState(AbstractRelationResolvingState parent) {
		return new FunctionalResolvingState(parent);
	}

	protected abstract RelationalFunction newComposingOperation();

	protected class FunctionalResolvingState extends AbstractRelationResolvingState {
		protected int index = 0;
		protected RelationalFunction composed;
		protected AbstractRelationResolvingState current;
		
		public FunctionalResolvingState(AbstractRelationResolvingState parent) {
			super(parent);
		}

		@Override
		public Object clone() {
			FunctionalResolvingState clone = (FunctionalResolvingState)super.clone();
			clone.composed = null;	// FIXME!!!!
			return clone;
		}

		@Override
		protected void reset() {
			super.reset();
			composed = null;
			current = null;
			index = 0;
		}
		
		@Override
		public AbstractRelationResolvingState next(IProgressMonitor monitor) {
			if(finished) { return parent != null ? parent : this; }
			if(composed == null) { composed = newComposingOperation(); }
			if(current == null) {
					AbstractSphere tb = (AbstractSphere)spheres.get(index++);
					current = tb.createResolverState(null);
			}
			if(!current.finished()) {
				current = current.next(monitor, relationName, arguments);
				return this;
			}

			// provide an opportunity to check progress is acceptable
			if(!checkResult()) {
				return parent;
			}
			if(current.getResult() != null) {
				composed.add(current.getResult());
			}
			current = null;
			if(finished = index >= spheres.size()) {
				if(composed.isEmpty()) {
					return parent != null ? parent : this;
				}
				result = composed;
				composed = null;
			}
			return this;
		}

		protected boolean checkResult() {
			return true;
		}


	}


}
