/*-******************************************************************************
 * Copyright (c) 2016 Iwao AVE!.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Iwao AVE! - initial API and implementation and/or initial documentation
 *******************************************************************************/

package net.harawata.mybatipse.bean;

import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.ITypeHierarchyChangedListener;
import org.eclipse.jdt.core.JavaModelException;

import net.harawata.mybatipse.Activator;

/**
 * @author Iwao AVE!
 */
public class SupertypeHierarchyCache
{
	private static final SupertypeHierarchyCache INSTANCE = new SupertypeHierarchyCache();

	private final ConcurrentHashMap<IType, ITypeHierarchy> cache = new ConcurrentHashMap<IType, ITypeHierarchy>();

	public boolean isCollection(IType type)
	{
		return isSubtype(type, "java.util.Collection");
	}

	public boolean isList(IType type)
	{
		return isSubtype(type, "java.util.List");
	}

	public boolean isMap(IType type)
	{
		return isSubtype(type, "java.util.Map");
	}

	public boolean isSubtype(IType type, String supertypeFqn)
	{
		try
		{
			IType supertype = type.getJavaProject().findType(supertypeFqn);
			return isSubtype(type, supertype);
		}
		catch (JavaModelException e)
		{
			// This can be safely ignored.
		}
		return false;
	}

	public boolean isSubtype(IType type, IType supertype)
	{
		if (supertype == null)
			return false;

		try
		{
			ITypeHierarchy supertypes = getSupertypes(type);
			return supertypes.contains(supertype);
		}
		catch (JavaModelException e)
		{
			Activator.log(Status.ERROR, "Error while checking type hierarchy of "
				+ type.getFullyQualifiedName() + " and " + supertype.getFullyQualifiedName(), e);
		}
		return false;
	}

	private ITypeHierarchy getSupertypes(final IType type) throws JavaModelException
	{
		ITypeHierarchy hierarchy = cache.get(type);
		if (hierarchy == null || !hierarchy.exists())
		{
			hierarchy = type.newSupertypeHierarchy(new NullProgressMonitor());
			hierarchy.addTypeHierarchyChangedListener(new ITypeHierarchyChangedListener()
			{
				@Override
				public void typeHierarchyChanged(ITypeHierarchy typeHierarchy)
				{
					cache.remove(type);
				}
			});
			cache.put(type, hierarchy);
		}
		return hierarchy;
	}

	public static SupertypeHierarchyCache getInstance()
	{
		return INSTANCE;
	}

	private SupertypeHierarchyCache()
	{
		super();
	}
}
