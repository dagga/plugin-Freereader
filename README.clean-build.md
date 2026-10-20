To get a completely clean build, you must compile the following and put them in lib/ :

jdom.jar (reference version 1.1.3)
jarjar-1.0.jar
jaxen-1.1.1.jar
rome-1.0.jar

To use the freenet jars from a different directory, create the file `override.properties` and define the locations there.

Example:
    freenet-ext.location = ../freenet/freenet-ext.jar
