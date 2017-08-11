package hello;

import easyframework.Component;
import hello.SampleService;

@Component
public class SampleServiceImpl implements SampleService {
    @Override
    public String hello() {
        return "Hello, Framework";
    }
}
