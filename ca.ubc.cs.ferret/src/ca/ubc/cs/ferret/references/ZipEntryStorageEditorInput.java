/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package ca.ubc.cs.ferret.references;


/* Stolen from org.eclipse.jdt.internal.debug.ui */
public class ZipEntryStorageEditorInput extends StorageEditorInput {
	
	public ZipEntryStorageEditorInput(ZipEntryStorage storage) {
		super(storage);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.IEditorInput#exists()
	 */
	public boolean exists() {
		return true;
	}

}
