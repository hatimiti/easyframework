package hello;

import javax.annotation.Resource;
import easyframework.EasyApplication;
import easyframework.Controller;
import easyframework.RequestMapping;

@Controller
public class SampleController {

    @Resource
    private SampleService service;

    @RequestMapping("/hello")
    public String home() {
        return service.hello();
    }

    public static void main(String[] args) {
        EasyApplication.run(SampleController.class, args);
    }
}

