@echo off 
pushd "%~dp0"
java -jar dsl-clc.jar -properties=compile.properties
popd
