package ru.korteng.finance_manager.dto;

import lombok.Data;
import ru.korteng.finance_manager.entity.Category;

@Data
public class CategoryResponse {

    private Long id;
    private String name;
    private Long parentId;

    public static CategoryResponse fromEntity(Category category) {
        CategoryResponse response = new CategoryResponse();
        response.setId(category.getId());
        response.setName(category.getName());
        if (category.getParent() != null) {
            response.setParentId(category.getParent().getId());
        }
        return response;
    }
}
