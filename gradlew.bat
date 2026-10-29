@rem Minimal Gradle wrapper launcher for Windows.
@echo off
setlocal

set APP_HOME=%~dp0
if "%JAVA_HOME%"=="" goto javaFromPath
set JAVACMD=%JAVA_HOME%\bin\java.exe
goto run

:javaFromPath
set JAVACMD=java

:run
"%JAVACMD%" -Xmx64m -Xms64m -Dorg.gradle.appname=gradlew ^
  -classpath "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" ^
  org.gradle.wrapper.GradleWrapperMain %*

endlocal
