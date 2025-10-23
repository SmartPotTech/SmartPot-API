package smartpot.com.api.Documentation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class DocumentController {
    @Value("${application.title}")
    private String title;

    @Value("${application.description}")
    private String description;

    @Value("${application.author}")
    private String author;

    @Value("${application.version}")
    private String version;

    @Value("${springdoc.api-docs.path}")
    private String specUrl;

    @Value("${springdoc.redoc.enabled}")
    private boolean redocEnabled;

    @Value("${springdoc.scalar.enabled}")
    private boolean scalarEnabled;

    @GetMapping("${springdoc.redoc.path}")
    public String redoc(Model model) {
        if (!redocEnabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "ReDoc está deshabilitado");
        }

        model.addAttribute("specUrl", specUrl);
        model.addAttribute("title", title);
        model.addAttribute("description", description);
        model.addAttribute("version", version);
        return "redoc";
    }

    @GetMapping("${springdoc.scalar.path}")
    public String scalar(Model model) {
        if (!scalarEnabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "ReDoc está deshabilitado");
        }

        model.addAttribute("specUrl", specUrl);
        model.addAttribute("title", title);
        model.addAttribute("description", description);
        model.addAttribute("version", version);
        return "scalar";
    }
}


