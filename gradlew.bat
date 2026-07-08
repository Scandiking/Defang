@rem Gradle startup script for Windows
@rem Add default JVM options here:
@set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"
@set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
