package kr.or.kosa.backend.tag.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

import kr.or.kosa.backend.tag.dto.TagDto;
import kr.or.kosa.backend.tag.service.TagService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tag")
public class TagController {

    private final TagService tagService;


}
