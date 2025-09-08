package dev.starq.picassolve.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DrawEvent {
    public double x1, y1, x2, y2;
    public double width;
    public String color;
    public String mode;
    public String actionId;
    public Boolean newStroke;
}
