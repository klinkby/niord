/*
 * Copyright 2016 Danish Maritime Authority.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.niord.core.area;

import com.vividsolutions.jts.geom.Geometry;
import org.apache.commons.lang.StringUtils;
import org.niord.core.geojson.JtsConverter;
import org.niord.core.model.VersionedEntity;
import org.niord.model.DataFilter;
import org.niord.model.ILocalizable;
import org.niord.model.message.AreaType;
import org.niord.model.message.AreaVo;
import org.niord.model.message.AreaVo.AreaMessageSorting;

import javax.persistence.Cacheable;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Transient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents a specific named area, part of an area-hierarchy
 */
@Entity
@Cacheable
@NamedQueries({
        @NamedQuery(name="Area.findByLegacyId",
                query = "select a FROM Area a where a.legacyId = :legacyId"),
        @NamedQuery(name  = "Area.findRootAreas",
                query = "select distinct a from Area a left join fetch a.children where a.parent is null order by a.siblingSortOrder"),
        @NamedQuery(name  = "Area.findAreasWithDescs",
                query = "select distinct a from Area a left join fetch a.descs order by a.parent, a.siblingSortOrder"),
        @NamedQuery(name  = "Area.findAreasWithIds",
                query = "select distinct a from Area a left join fetch a.descs where a.id in (:ids)"),
        @NamedQuery(name  = "Area.findByMrn",
                query = "select a from Area a left join fetch a.descs where a.mrn = :mrn"),
        @NamedQuery(name  = "Area.findLastUpdated",
                query = "select max(a.updated) from Area a")
})
@SuppressWarnings("unused")
public class Area extends VersionedEntity<Integer> implements ILocalizable<AreaDesc>, Comparable<Area> {

    String legacyId;

    String mrn;

    AreaType type;

    boolean active = true;

    @ManyToOne(cascade = { CascadeType.PERSIST, CascadeType.MERGE, CascadeType.DETACH })
    private Area parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
    @OrderBy("siblingSortOrder ASC")
    private List<Area> children = new ArrayList<>();

    @Column(columnDefinition = "GEOMETRY")
    Geometry geometry;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "entity", orphanRemoval = true)
    List<AreaDesc> descs = new ArrayList<>();

    @Column(length = 256)
    String lineage;

    // The sortOrder is used to sort this area among siblings, and exposed via the Admin UI
    @Column(columnDefinition="DOUBLE default 0.0")
    double siblingSortOrder;

    // The treeSortOrder is re-computed at regular intervals by the system and designates
    // the index of the area in an entire sorted area tree. Used for area sorting.
    @Column(columnDefinition="INT default 0")
    int treeSortOrder;

    AreaMessageSorting messageSorting;
    Float originLatitude;   // For CW and CCW message sorting
    Float originLongitude;  // For CW and CCW message sorting
    Integer originAngle;    // For CW and CCW message sorting


    @ElementCollection
    List<String> editorFields = new ArrayList<>();

    /** Constructor */
    public Area() {
    }


    /** Constructor */
    public Area(AreaVo area) {
        this(area, DataFilter.get().fields("geometry"));
    }


    /** Constructor */
    public Area(AreaVo area, DataFilter filter) {
        updateArea(area, filter);
    }


    /** Updates this area from the given area */
    public void updateArea(AreaVo area, DataFilter filter) {

        DataFilter compFilter = filter.forComponent(Area.class);

        this.mrn = area.getMrn();
        this.type = area.getType();
        this.active = area.isActive();
        this.id = area.getId();
        this.siblingSortOrder = area.getSiblingSortOrder() == null ? 0 : area.getSiblingSortOrder();
        this.messageSorting = area.getMessageSorting();
        this.originLatitude = area.getOriginLatitude();
        this.originLongitude = area.getOriginLongitude();
        this.originAngle = area.getOriginAngle();

        if (compFilter.includeGeometry()) {
            this.geometry = JtsConverter.toJts(area.getGeometry());
        }
        if (compFilter.includeParent() && area.getParent() != null) {
            parent = new Area(area.getParent(), filter);
        }
        if (compFilter.includeChildren() && area.getChildren() != null) {
            area.getChildren().stream()
                    .map(a -> new Area(a, filter))
                    .forEach(this::addChild);
        }
        if (area.getDescs() != null) {
            area.getDescs()
                    .forEach(desc -> createDesc(desc.getLang()).setName(desc.getName()));
        }
        if (area.getEditorFields() != null) {
            editorFields.addAll(area.getEditorFields());
        }
    }


    /** Converts this entity to a value object */
    public AreaVo toVo(DataFilter filter) {

        DataFilter compFilter = filter.forComponent(Area.class);

        AreaVo area = new AreaVo();
        area.setId(id);
        area.setMrn(mrn);
        area.setActive(active);

        if (compFilter.includeDetails()) {
            area.setType(type);
            area.setSiblingSortOrder(siblingSortOrder);
            area.setMessageSorting(messageSorting);
            area.setOriginLatitude(originLatitude);
            area.setOriginLongitude(originLongitude);
            area.setOriginAngle(originAngle);

            if (!editorFields.isEmpty()) {
                area.setEditorFields(new ArrayList<>(editorFields));
            }
        }

        if (compFilter.includeGeometry()) {
            area.setGeometry(JtsConverter.fromJts(geometry));
        }

        if (compFilter.includeChildren()) {
            children.forEach(child -> area.checkCreateChildren().add(child.toVo(filter)));
        }

        if (compFilter.includeParent() && parent != null) {
            area.setParent(parent.toVo(filter));
        } else if (compFilter.includeParentId() && parent != null) {
            AreaVo parent = new AreaVo();
            parent.setId(parent.getId());
            area.setParent(parent);
        }

        if (!descs.isEmpty()) {
            area.setDescs(getDescs(filter).stream()
                .map(AreaDesc::toVo)
                .collect(Collectors.toList()));
        }

        return area;
    }


    /**
     * Checks if the values of the area has changed.
     * Only checks relevant values, not e.g. database id, created date, etc.
     * Hence we do not use equals()
     *
     * @param template the template to compare with
     * @return if the area has changed
     */
    @Transient
    public boolean hasChanged(Area template) {
        return !Objects.equals(siblingSortOrder, template.getSiblingSortOrder()) ||
                !Objects.equals(mrn, template.getMrn()) ||
                !Objects.equals(type, template.getType()) ||
                !Objects.equals(active, template.isActive()) ||
                descsChanged(template) ||
                parentChanged(template) ||
                geometryChanged(template);
    }


    /** Checks if the geometry has changed */
    protected boolean geometryChanged(Area template) {
        if (geometry == null && template.getGeometry() == null) {
            return false;
        } else if (geometry == null || template.getGeometry() == null) {
            return true;
        }
        return !geometry.equals(template.getGeometry());
    }


    /** Checks if the geometry has changed */
    private boolean descsChanged(Area template) {
        return descs.size() != template.getDescs().size() ||
                descs.stream()
                    .anyMatch(d -> template.getDesc(d.getLang()) == null ||
                            !Objects.equals(d.getName(), template.getDesc(d.getLang()).getName()));
    }

    /** Checks if the parents have changed */
    private boolean parentChanged(Area template) {
        return (parent == null && template.getParent() != null) ||
                (parent != null && template.getParent() == null) ||
                (parent != null && template.getParent() != null &&
                        !Objects.equals(parent.getId(), template.getParent().getId()));
    }

    /** {@inheritDoc} */
    @Override
    public AreaDesc createDesc(String lang) {
        AreaDesc desc = new AreaDesc();
        desc.setLang(lang);
        desc.setEntity(this);
        getDescs().add(desc);
        return desc;
    }


    /**
     * Adds a child area, and ensures that all references are properly updated
     *
     * @param area the area to add
     */
    public void addChild(Area area) {
        // Add the area to the end of the children list
        Area lastChild = children.isEmpty() ? null : children.get(children.size() - 1);

        // Give it initial tree sort order. Won't really be correct until the tree sort order has
        // been re-computed for the entire tree.
        area.setTreeSortOrder(lastChild == null ? treeSortOrder : lastChild.getTreeSortOrder());

        children.add(area);
        area.setParent(this);
    }


    /**
     * Update the lineage to have the format "/root-id/.../parent-id/id"
     * @return if the lineage was updated
     */
    public boolean updateLineage() {
        String oldLineage = lineage;
        lineage = getParent() == null
                ? "/" + id + "/"
                : getParent().getLineage() + id + "/";
        return !lineage.equals(oldLineage);
    }


    /**
     * If the area is active, ensure that parent areas are active.
     * If the area is inactive, ensure that child areas are inactive.
     */
    @SuppressWarnings("all")
    public void updateActiveFlag() {
        if (active) {
            // Ensure that parent areas are active
            if (getParent() != null && !getParent().isActive()) {
                getParent().setActive(true);
                getParent().updateActiveFlag();
            }
        } else {
            // Ensure that child areas are inactive
            getChildren().stream()
                    .filter(Area::isActive)
                    .forEach(child -> {
                child.setActive(false);
                child.updateActiveFlag();
            });
        }
    }


    /** {@inheritDoc} */
    @Override
    @SuppressWarnings("all")
    public int compareTo(Area area) {
        return (area == null || siblingSortOrder == area.getSiblingSortOrder())
                ? 0
                : (siblingSortOrder < area.getSiblingSortOrder() ? -1 : 1);
    }


    /**
     * Checks if this is a root area
     *
     * @return if this is a root area
     */
    @Transient
    public boolean isRootArea() {
        return parent == null;
    }


    /**
     * Returns the lineage of this area as a list, ordered with this area first, and the root-most area last
     * @return the lineage of this area
     */
    public List<Area> lineageAsList() {
        List<Area> areas = new ArrayList<>();
        for (Area a = this; a != null; a = a.getParent()) {
            areas.add(a);
        }
        return areas;
    }

    /**
     * By convention, a list of areas will be emitted top-to-bottom. So, if we have a list with:
     * [ Kattegat -> Danmark, Skagerak -> Danmark, Hamborg -> Tyskland ], the resulting title
     * line should be: "Danmark. Tyskland. Kattegat. Skagerak. Hamborg."
     * @param areas the areas to compute a title line prefix for
     * @return the title line prefix
     */
    public static String computeAreaTitlePrefix(List<Area> areas, String language) {
        List<List<String>> areaNames = new ArrayList<>();
        int maxLevels = 0;
        for (Area a  : areas) {
            List<String> areaLineageNames = new ArrayList<>();
            for (Area area = a; area != null; area = area.getParent()) {
                AreaDesc desc = area.getDescs(DataFilter.get().lang(language)).get(0);
                if (!StringUtils.isBlank(desc.getName())) {
                    areaLineageNames.add(desc.getName());
                }
            }
            Collections.reverse(areaLineageNames);
            areaNames.add(areaLineageNames);
            maxLevels = Math.max(maxLevels, areaLineageNames.size());
        }

        StringBuilder str = new StringBuilder();
        for (int x = 0; x < maxLevels; x++) {
            Set<String> emittedNames = new HashSet<>();
            for (List<String> areaLineageNames : areaNames) {
                if (areaLineageNames.size() > x) {
                    String name = areaLineageNames.get(x);
                    if (!emittedNames.contains(name)) {
                        emittedNames.add(name);
                        str.append(name);
                        if (!name.endsWith(".")) {
                            str.append(".");
                        }
                        str.append(" ");
                    }
                }
            }
        }

        return str.toString().trim();
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getLegacyId() {
        return legacyId;
    }

    public void setLegacyId(String legacyId) {
        this.legacyId = legacyId;
    }

    public String getMrn() {
        return mrn;
    }

    public void setMrn(String mrn) {
        this.mrn = mrn;
    }

    public AreaType getType() {
        return type;
    }

    public void setType(AreaType type) {
        this.type = type;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public List<AreaDesc> getDescs() {
        return descs;
    }

    @Override
    public void setDescs(List<AreaDesc> descs) {
        this.descs = descs;
    }

    public Area getParent() {
        return parent;
    }

    public void setParent(Area parent) {
        this.parent = parent;
    }

    public List<Area> getChildren() {
        return children;
    }

    public void setChildren(List<Area> children) {
        this.children = children;
    }

    public Geometry getGeometry() {
        return geometry;
    }

    public void setGeometry(Geometry geometry) {
        this.geometry = geometry;
    }

    public String getLineage() {
        return lineage;
    }

    public void setLineage(String lineage) {
        this.lineage = lineage;
    }

    public double getSiblingSortOrder() {
        return siblingSortOrder;
    }

    public void setSiblingSortOrder(double sortOrder) {
        this.siblingSortOrder = sortOrder;
    }

    public int getTreeSortOrder() {
        return treeSortOrder;
    }

    public void setTreeSortOrder(int treeSortOrder) {
        this.treeSortOrder = treeSortOrder;
    }

    public AreaMessageSorting getMessageSorting() {
        return messageSorting;
    }

    public void setMessageSorting(AreaMessageSorting messageSorting) {
        this.messageSorting = messageSorting;
    }

    public Float getOriginLatitude() {
        return originLatitude;
    }

    public void setOriginLatitude(Float originLatitude) {
        this.originLatitude = originLatitude;
    }

    public Float getOriginLongitude() {
        return originLongitude;
    }

    public void setOriginLongitude(Float originLongitude) {
        this.originLongitude = originLongitude;
    }

    public Integer getOriginAngle() {
        return originAngle;
    }

    public void setOriginAngle(Integer originAngle) {
        this.originAngle = originAngle;
    }

    public List<String> getEditorFields() {
        return editorFields;
    }

    public void setEditorFields(List<String> editorFields) {
        this.editorFields = editorFields;
    }
}

