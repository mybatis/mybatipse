/*-******************************************************************************
 * Copyright (c) 2014 Iwao AVE!.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Iwao AVE! - initial API and implementation and/or initial documentation
 *******************************************************************************/

package net.harawata.mybatipse.mybatis;

import java.util.List;

import org.eclipse.jdt.core.dom.MethodDeclaration;

/**
 * @author Iwao AVE!
 */
public class MapperMethod
{
	private MethodDeclaration methodDeclaration;

	private String statement;

	public MethodDeclaration getMethodDeclaration()
	{
		return methodDeclaration;
	}

	public void setMethodDeclaration(MethodDeclaration methodDeclaration)
	{
		this.methodDeclaration = methodDeclaration;
	}

	public String getStatement()
	{
		return statement;
	}

	public void setStatement(String statement)
	{
		this.statement = statement;
	}

	@SuppressWarnings("rawtypes")
	public List parameters()
	{
		return this.methodDeclaration.parameters();
	}
}
