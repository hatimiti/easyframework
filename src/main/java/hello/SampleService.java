package hello;

import easyframework.Component;
import easyframework.Transactional;

public interface SampleService {
    @Transactional
    String hello();
}
