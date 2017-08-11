package hello;

import hello.SampleService;

public class DummyService implements SampleService {
    @Override
    public String hello() {
        return "Hello, Dummy";
    }
}
