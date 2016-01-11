MyBatipse : Eclipse plugin for MyBatis
=======

[MyBatis](http://mybatis.org/) is a popular SQL Mapper framework.  
And __MyBatipse__ is an Eclipse plug-in which provids content assists and validations when editing MyBatis related files.

## Features

### XML Editor Enhancements
#### Auto-completion

- Mapper namespace : Calculated from the path.  
![autocomplete namespace](screen/ac-ns1.png)![autocomplete namespace](screen/ac-ns2.png)
- Java class/Type alias : Propose Java classes and Type Aliases (translucent icons). Camelcase match is supported. [1] [2]  
![autocomplete class](screen/ac-class.png) ![autocomplete class](screen/ac-class-cc.png)
- Java property : Propose properties of the parent java class. Nested properties are supported.  
![autocomplete property](screen/ac-prop1.png) ![autocomplete property](screen/ac-prop2.png)
- Statement ID : If corresponding mapper interface exists, propose the method name as a statement ID.  
![autocomplete statement id](screen/ac-stmt.png)
- Reference to resultMap/select/sql : Propose ID of the referenced resultMap/select/sql. External reference is supported (blue icons).  
![autocomplete reference](screen/ac-ref.png)
- Parameter properties : #{} and ${} in select/insert/udpate/delte statements. Also works in some attributes.  
![autocomplete properties](screen/ac-stmt-prop-xml.png) ![autocomplete properties 2](screen/ac-prop-test.png)
- javaType, jdbcType, typeHandler in #{}  
![autocomplete jdbcType](screen/ac-jdbc-type.png)
- Result elements in resultMap/collection/association : Generates <result /> elements for the parent java class properties.  
![autocomplete results 1](screen/ac-results1.png) ![autocomplete results 2](screen/ac-results2.png)

- __[1]__ For better performance, classes in the application libraries (loaded as JARs) are listed only when at least one package segment is specified.
- __[2]__ There is a limitation for the type alias support. Basically, if type aliases are registered using Java API, they are not automatically detected. As a workaround, you can register type aliases in MyBatipse's preference. See the _Configuration_ section.

#### Hyperlinks
- To referenced resultMap/select/sql element. External reference is supported.  
![link to external reference](screen/hl-refid.png)
- From/to Java/XML declaration.  
![link to xml mapper](screen/hl-xml-mapper.png)  
![link to xml statement](screen/hl-xml-statement.png)  
![link to java method](screen/hl-java-method.png)


#### Validation
- Missing TypeAlias, Java class/property.
- Missing resultMap/select/sql ID. 
- etc.   
![refid error](screen/err-refid.png)
![problem view](screen/prblm-view.png)

#### Mapper Declaration View
- This feature is ported from [MyBatis Editor plugin](https://code.google.com/a/eclipselabs.org/p/mybatiseditor/) by @peterhendriks .  
![sqlview xml](screen/sqlview-xml.png)
![sqlview](screen/sqlview.png)


### Java Editor Enhancements

#### Auto-completion
- Parameter properties : #{} and ${} in select/insert/udpate/delte annotations.
![autocomplete properties in java](screen/ac-stmt-prop-java.png)  
- javaType, jdbcType, typeHandler in #{}  : See the XML example.
- Reference to resultMap in @ResultMap  
![autocomplete resultmap annotation](screen/ac-rmap-java.png)  


#### Quick Assist
- Add @Param to method parameters : Put the cursor on the method name and press cmd + 1 (or ctrl + 1).  
![param-annotation1](screen/qa-add-param1.png)  
![param-annotation2](screen/qa-add-param2.png)  


## Configuration

### XML mapper location
- MyBatipse searches XML mapper files under your project's __Source Folder__. To make a directory __Source Folder__, right click the directory in the package explorer and choose __Build Path__ -> __Use as Source Folder__ from the menu.

### MyBatis Nature
- By default, each file is validated when it's saved, but you would want to validate the XML mapper when the related Java classes are updated as well. For that purpose, MyBatipse provides a custom nature __MyBatis Nature__.  
To add the nature, right click the project in the package explorer and choose __Configure__ -> __Add MyBatis Nature__ from the menu.   
![nature](screen/nature.png)

### Type Alias
- Type alias are automatically detected _if_ they are registered using XML config file (both mybatis and mybatis-spring config is supported).  
If you register type aliases in Java code, MyBatipse cannot detect them. As a workaround, you can register custom type aliases in the project setting (Open __Project__ -> __Properties__ menu and select __MyBatipse__ from the left column).  
![type aliases](screen/pref-alias.png)   
For example, with the above settings: 
  - The first entry registers all the classes in ```com.example.domain``` package as type aliases.  
  - The second entry registers the single class ```domain.Person```.  
  - The third entry registers the single class ```domain.SomeLongNamedBean``` under the alias ```SomeBean```.
  - Note that, in any case, MyBatipse respects ```@Alias``` annotation if it exists.

## Tips
- To move MyBatis proposals to the top of the XML proposal list, quit Eclipse and open the following file in the workspace: ```.metadata/.plugins/org.eclipse.core.runtime/.settings/org.eclipse.wst.xml.ui.prefs``` Find the line started with ```xml_content_assist_default_page_sort_order=``` and insert ```net.harawata.mybatis.proposalCategory.xml\u0000``` right after the equal sign.  
![xml proposal order](screen/xml-proposal-order.png)  



## Installation

There are several ways to install MyBatipse.

- Drag this button [![drag-to-install button](http://marketplace.eclipse.org/misc/installbutton.png)](http://marketplace.eclipse.org/marketplace-client-intro?mpc_install=1467819)  from browser to your Eclipse workbench (i.e. window).
- Choose __Eclipse Marketplace...__ in __Help__ menu of your Eclipse and search __MyBatipse__.
- Choose __Install New Software...__ in __Help__ menu and enter the following URL.  
http://dl.bintray.com/harawata/eclipse

## Bugs, questions, suggestions, etc.
Please use the [issue tracker](https://github.com/harawata/mybatipse/issues).  


## License
MyBatipse is licensed under [__Eclipse Public License 1.0__](http://www.eclipse.org/legal/epl-v10.html).
