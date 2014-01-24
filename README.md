Java Loggregator
================

A Loggregator client that runs on the Java platform.

To emit event to Loggregator in your project, add the following to your Maven pom.xml:

```xml
<dependency>
    <groupId>com.github.cloudfoundry-community</groupId>
    <artifactId>loggregator</artifactId>
    <version>0.1</version>
</dependency>
```

## Basic Usage

```java
final Emitter emitter = new EmitterBuilder("10.1.2.3", 3456, "loggregator_secret")
    .sourceName("TEST")
    .build();
emitter.emit("9942b30c-bc4d-4349-9fdd-2ba33b05e1a5", "This is a test.");

```
