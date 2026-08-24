@rem Gradle startup script for Windows
@if "%DEBUG%"=="" @echo off
set JAVA_EXE=%JAVA_HOME%\bin\java.exe
set CLASSPATH=%~dp0gradle\wrapper\gradle-wrapper.jar
"%JAVA_EXE%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
