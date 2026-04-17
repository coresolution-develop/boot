package com.coresolution.pe.controller.admin;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.coresolution.pe.entity.OrgProgressRow;
import com.coresolution.pe.service.AdminProgressByOrgService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class AdminProgressByOrgController {

    private final AdminProgressByOrgService service;

}
