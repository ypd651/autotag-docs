package com.autotag.docs.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping(value = {"/", "/dashboard", "/search", "/triggers"})
    public String index() {
        return "forward:/index.html";
    }
}
