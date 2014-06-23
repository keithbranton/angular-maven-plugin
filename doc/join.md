join
====

This goal is designed to build modular angularjs applications that are intended to be lazy loaded. Angularjs doesn't really support lazy loading of angular modules at this point. I have a customized router which manages this for me and I use angularjs pull request #4694 to allow modules to be added to the injector after bootstrap. 

There are probably a lot of assumptions made about project layout so I'm going to describe how I organize my angularjs code and what the results are.

src/main/js/
|__app.js
|__main.js
|__first
|  |__firstModule.js
|  |__template1.html
|  |__firstDirectives.js
|__second
|  |__secondModule.js
|  |__template2.js
|  |__template3.js
|__utility
   |__commonDirectives.js
   |__commonServices.js 
   |__template4.js
   |__template5.js

When I use this goal...

	<plugin>
		<groupId>com.keithbranton.mojo</groupId>
		<artifactId>angular-maven-plugin</artifactId>
		<version>0.3.1</version>
		<executions>
			<execution>
				<phase>generate-sources</phase>
				<goals>
					<goal>join</goal>
				</goals>
				<configuration>
					<source>src/main/js/</source>
					<target>${target.dir}/js/</target>
					<templates>*.html,utility/*.html</templates>
				</configuration>
			</execution>
		</executions>
	</plugin>

...it produces:

target/js/
|__app.js
|__main.js
|__firstModule.js
|__secondModule.js

**firstModule.js** declares a dependency on "/js/first/firstDirectives.js" to requirejs in a declare function. The declare is parsed (pretty naively, using a regex). The resulting firstModule.js will consist of firstDirectives.js, wrapped in an immediate function to prevent any contamination, then a template cache insertion statement like the html2js goal generates, then will return the result of an immediate function wrapping firstModule.js.

**secondModule.js** will include the templates 2 and 3 and secondModule the same way as firstModule.js does

When these modules are combined a new define call is generated combining all the external requirejs dependencies of all the combined file in what is hopefully a sensible way.

**app.js** will contain commonDirectives and commonServices because it refers to them. It will also include templates 4 and 5 because they are in the utility folder and the templates pattern we provided in the goal configuration includes them.

Assuming **main.js** contains content such as

require.config({
	paths : {
		'FirstModule' : '/js/first/firstModule',
		'SecondModule' : '/js/second/secondModule'
	}
});

this will be changed to 

require.config({
	paths : {
		'FirstModule' : '/js/firstModule',
		'SecondModule' : '/js/secondModule'
	}
});

Configuration Options
---------------------

source - the folder containing the source code and templates - defaults to /src/main/js
main - the name of the main file - defaults to main.js
app - the name of the app file - defaults to app.js
modules - a comma separated list of glob patterns that identify the starting point for a module, defaults to **/*Module.js
templates - a comma separated list of glob patterns that identify html templates - defaults to *.html
joinable - a comma separated list of glob patterns that identify dependencies that should be joined, defaults to /js/**/*.js/
target - where to put the resulting files
prefix - a prefix to add to all the template cache keys  

Using join with Eclipse (kepler)
-----------------------------------

As I work I like to generate these files incrementally whenever any of their source files are changed. The goal is designed to be incremental build aware. To use it add the following section to your pom...

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
											<goal>join</goal>
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

With that in place you should be able to make a change to a file and see the result in the corresponding target file as soon as you save the change.

The goal adds some info to the maven log, which can be seen in the eclipse console if you choose the maven console. This can be helpful in diagnosing misconfiguration-type problems.

Changes
-------

Feel free to fork if you'd rather take the goal in a different direction. If you'd like to send me pull requests for improvements that you think could benefit the community I'd be happy to consider them.