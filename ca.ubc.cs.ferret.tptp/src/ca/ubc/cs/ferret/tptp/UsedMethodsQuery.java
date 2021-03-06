package ca.ubc.cs.ferret.tptp;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;

import ca.ubc.cs.ferret.model.AbstractSingleParmConceptualQuery;
import ca.ubc.cs.ferret.model.IRelation;
import ca.ubc.cs.ferret.model.SimpleSolution;
import ca.ubc.cs.ferret.types.FerretObject;

public class UsedMethodsQuery extends AbstractSingleParmConceptualQuery<FerretObject> {

	public UsedMethodsQuery() {}

	@Override
	protected void internalRun(IProgressMonitor monitor) {
        IRelation methodsExecuted = getSphere().resolve(new SubProgressMonitor(monitor, 10),
        		TptpSphereHelper.OP_USED_METHODS, parameter);
        for(FerretObject m : methodsExecuted) {
            SimpleSolution s = new SimpleSolution(this, this);
            s.add("executed", m);
            addSolution(s);
        }
	}

	public String getDescription() {
		return "used methods";
	}

	public boolean isValid() {
		return true;
	}
}
