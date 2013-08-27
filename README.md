angular-maven-plugin
====================

An eclipse compatible maven plugin that mimics grunt-html2js in combining html templates into a single javascript file for use with Angular.js

This plugin currently only exposes one goal - html2js. 

The html2js goal allows the specification of a source directory, include and exclude file patterns, and a target. It will collect all the html from the source directory that satisfies the include/exclude requirements and convert them into javascript statements and write them to target. If you use require.js (as I do) then you can set the addRequireWrapper flag that will cause the code to be wrapped in the appropriate declare.

The plugin is not in any maven repos yet. If there is any demand I will look into how best to make it publicly available (though I'd also appreciate suggestions as this is the first maven plugin I've open-sourced). If you'd like to use it, the easiest way would be to clone the git repo, and run...

> mvn clean install

...to install the plugin in your local repo. (I'm assuming familiarity with maven if you are interested in this plugin, and that you know how to clone git repos if you are on github :)

To use the plugin add the following to the pom of the project containing the templates...

	<build>
		<plugins>
			<plugin>
				<groupId>com.keithbranton.mojo</groupId>
				<artifactId>angular-maven-plugin</artifactId>
				<version>0.1-SNAPSHOT</version>
				<executions>
					<execution>
						<phase>generate-resources</phase>
						<goals>
							<goal>html2js</goal>
						</goals>
					</execution>
				</executions>
				<configuration>
					<sourceDir>${basedir}/src/main/template/</sourceDir>
					<include>**/*.html</include>
					<exclude>not-this.html</exclude>
					<target>${basedir}/src/main/generated/js/templates.js</target>
					<addRequireWrapper>true</addRequireWrapper>
				</configuration>
			</plugin>
		  ...

The above shows examples of the configuration parameters available. The values shown for sourceDir, include and target are the default values provided by the plugin. By default there is no exclude, and addRequireWrapper is false.

Using html2js with Eclipse (kepler)
-----------------------------------

As I work I like the template.js file to be updated whenever I add/change/delete a template. The plugin is designed to be incremental build aware. To use it add the following section to your pom...

  <build>
		<pluginManagement>
			<plugins>
				<plugin>
					<groupId>org.eclipse.m2e</groupId>
					<artifactId>lifecycle-mapping</artifactId>
					<version>1.0.0</version>
					<configuration>
						<lifecycleMappingMetadata>
							<pluginExecutions>
								<pluginExecution>
									<pluginExecutionFilter>
										<groupId>com.keithbranton.mojo</groupId>
										<artifactId>angular-maven-plugin</artifactId>
										<versionRange>[0.1-SNAPSHOT,)</versionRange>
										<goals>
											<goal>html2js</goal>
										</goals>
									</pluginExecutionFilter>
									<action>
										<execute>
											<runOnIncremental>true</runOnIncremental>
										</execute>
									</action>
								</pluginExecution>
							</pluginExecutions>
						</lifecycleMappingMetadata>
					</configuration>
				</plugin>
        ...

With that in place you should be able to make a change to a template and see the result in your target file as soon as you save the change.

The plugin adds some info to the maven log, which can be seen in the eclipse console if you choose the maven console. This can be helpful in diagnosing misconfiguration-type problems.

Changes
-------

Feel free to fork if you'd rather take the plugin in a different direction. If you'd like to send me pull requests for improvements that you think could benefit the community I'd be happy to consider them.
