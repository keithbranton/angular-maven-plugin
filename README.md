angular-maven-plugin
====================

A plugin designed to help developers who are deploying angularjs applications, but use maven as a build tool. So far there are two goals:

[html2js](doc/html2js.md)
-------
Mimics grunt-html2js in combining html templates into a single javascript file for use with Angular.js. It does NOT use grunt or node.

[join](doc/join.md)
----
a more complex goal designed to simplify assembly of a large modular angularjs application where modules are lazy loaded. The goal only deals with the reorganization of the code, not the lazy loading itself. 

Usage
-----
 
This plugin is hosted in Maven Central...

    <plugin>
        <groupId>com.keithbranton.mojo</groupId>
        <artifactId>angular-maven-plugin</artifactId>
        <version>0.3.3</version>
    </plugin>
