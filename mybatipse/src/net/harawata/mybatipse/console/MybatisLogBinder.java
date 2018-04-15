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

package net.harawata.mybatipse.console;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Iwao AVE!
 */
public abstract class MybatisLogBinder
{
	private static final String PREFIX_SQL = "==>  Preparing:";

	private static final String PREFIX_PARAMS = "==> Parameters:";

	private static final String TERMINATOR = "<==";

	private static final List<String> nonQuotedTypes = Arrays.asList("Integer", "Long", "Double",
		"Float", "Boolean");

	public static String bind(String src)
	{
		String statement = extractStatementLine(src);
		if (statement == null || statement.length() == 0)
		{
			return null;
		}
		String parametersLine = extractParametersLine(src);
		List<String> params = extractParameters(parametersLine);
		if (params == null || params.size() == 0)
		{
			return statement;
		}
		else
		{
			// replace '?' with parameters.
			// it does not support statement contains non-placeholder question marks.
			StringBuilder sql = new StringBuilder();
			String[] segments = statement.split("\\?");
			for (int i = 0; i < segments.length; i++)
			{
				sql.append(segments[i]);
				if (i < params.size())
				{
					sql.append(params.get(i));
				}
			}
			return sql.toString();
		}
	}

	protected static String extractStatementLine(String src)
	{
		final int sqlPrefixStart = src.indexOf(PREFIX_SQL);
		if (sqlPrefixStart == -1)
		{
			throw new IllegalArgumentException(
				"Couldn't find the prefix of SQL statement line: '" + PREFIX_SQL + "'");
		}
		final int sqlStart = sqlPrefixStart + PREFIX_SQL.length();
		final int sqlEnd = src.indexOf('\n', sqlStart);
		if (sqlEnd == -1)
		{
			throw new IllegalArgumentException("Couldn't find the end of the SQL statement line.");
		}
		return src.substring(sqlStart, sqlEnd).trim();
	}

	protected static String extractParametersLine(String src)
	{
		int paramsPrefixStart = src.indexOf(PREFIX_PARAMS);
		if (paramsPrefixStart == -1)
		{
			throw new IllegalArgumentException(
				"Couldn't find the prefix of parameters line: '" + PREFIX_PARAMS + "'");
		}
		int paramsStart = paramsPrefixStart + PREFIX_SQL.length();
		// Nested selects.
		int nextSqlStartPos = src.indexOf(PREFIX_SQL, paramsStart);
		int terminatorPos = src.indexOf(TERMINATOR, paramsStart);
		if (nextSqlStartPos > -1 && (nextSqlStartPos < terminatorPos || terminatorPos == -1))
		{
			terminatorPos = nextSqlStartPos;
		}
		else if (terminatorPos == -1)
		{
			throw new IllegalArgumentException(
				"Failed to parse the parameters. Be sure to include the string '" + TERMINATOR
					+ "' in the selection.");
		}
		int paramsEnd = src.lastIndexOf('\n', terminatorPos);
		return src.substring(paramsStart, paramsEnd).trim();
	}

	protected static List<String> extractParameters(String src)
	{
		if (src == null || src.length() == 0)
		{
			return null;
		}
		// it does not work if a string parameter contains '), ' as literal.
		List<String> params = new ArrayList<String>();
		int cursor = src.length();
		while (cursor > -1)
		{
			if (src.lastIndexOf("null", cursor) == cursor - 4)
			{
				params.add(0, "null");
				cursor = src.lastIndexOf("null", cursor) - 2;
			}
			else if (src.charAt(cursor - 1) == ')')
			{
				int typeStart = src.lastIndexOf('(', cursor);
				String type = src.substring(typeStart + 1, cursor - 1);

				cursor = typeStart;
				int valueStart = src.lastIndexOf(", ", cursor);
				while (valueStart > -1 && src.lastIndexOf("null", valueStart) != valueStart - 4
					&& src.charAt(valueStart - 1) != ')')
				{
					valueStart = src.lastIndexOf(", ", valueStart - 1);
				}
				String value = src.substring(valueStart == -1 ? 0 : valueStart + 2, cursor);
				cursor = valueStart;
				if (nonQuotedTypes.contains(type))
				{
					params.add(0, value);
				}
				else
				{
					params.add(0, "'" + value + "'");
				}
			}
			else
			{
				cursor = -1;
			}
		}
		return params;
	}
}
