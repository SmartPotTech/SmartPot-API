package smartpot.com.api.Controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.view.RedirectView;

@Controller
public class IndexController {
    @GetMapping({"/","","home","Home"})
    public String swaggerUi() {
        return "redirect:/swagger-ui/index.html";
    }
}
