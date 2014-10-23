#!/bin/bash
cd "$( dirname "$0" )" 
exec java -jar dsl-clc.jar -properties=compile.properties
