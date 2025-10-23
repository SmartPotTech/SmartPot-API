package smartpot.com.api.Documentation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RedocController {
    @Value("${application.title}")
    private String title;

    @Value("${application.description}")
    private String description;

    @Value("${application.author}")
    private String author;

    @Value("${application.version}")
    private String version;

    @Value("${springdoc.api-docs.path:/v3/api-docs}")
    private String specUrl;

    @GetMapping("${springdoc.redoc.path}")
    public String redoc(Model model) {
        model.addAttribute("specUrl", specUrl);
        model.addAttribute("title", title);
        model.addAttribute("description", description);
        model.addAttribute("author", author);
        model.addAttribute("version", version);
        return "redoc";
    }
}


