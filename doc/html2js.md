html2js
=======

A maven plugin goal that mimics grunt-html2js in combining html templates into a single javascript file for use with Angular.js. It does NOT use grunt or node.

The html2js goal allows the specification of a source directory, include and exclude file patterns, and a target. It will collect all the html from the source directory that satisfies the include/exclude requirements and convert them into javascript statements and write them to target. If you use require.js (as I do) then you can set the addRequireWrapper flag that will cause the code to be wrapped in the appropriate declare.

To use the plugin add the following to the pom of the project containing the templates...

	<build>
		<plugins>
			<plugin>
				<groupId>com.keithbranton.mojo</groupId>
				<artifactId>angular-maven-plugin</artifactId>
				<version>0.3.2</version>
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
					<prefix>/templateCachePrefix</prefix>
				</configuration>
			</plugin>
		  ...

The above shows examples of the configuration parameters available. The values shown for sourceDir, include and target are the default values provided by the plugin. By default there is no exclude or prefix and addRequireWrapper is false.

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

Thanks
------

Thanks to jkorri for adding the optional <prefix> configuration option, which adds the supplied prefix to the start of each template name in the template cache.

Thanks to cybercomkvint for reporting and supplying a pull request to correct a windows incompatibility.
