/* Copyright (c) 2011 Danish Maritime Authority
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.niord.core.area;

import com.vividsolutions.jts.geom.Geometry;
import org.niord.core.model.VersionedEntity;
import org.niord.core.util.GeoJsonUtils;
import org.niord.model.DataFilter;
import org.niord.model.ILocalizable;
import org.niord.model.vo.AreaVo;

import javax.persistence.Cacheable;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Transient;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents a specific named area, part of an area-hierarchy
 */
@Entity
@Cacheable
@NamedQueries({
        @NamedQuery(name  = "Area.searchAreas",
                query = "select distinct a from Area a left join a.descs d where d.lang = :lang and lower(d.name) like lower(:term) "
                      + "order by locate(lower(:sort), lower(d.name))"),
        @NamedQuery(name  = "Area.findRootAreas",
                query = "select distinct a from Area a left join fetch a.children where a.parent is null"),
        @NamedQuery(name  = "Area.findAreasWithDescs",
                query = "select distinct a from Area a left join fetch a.descs order by a.parent, a.siblingSortOrder"),
        @NamedQuery(name  = "Area.findLastUpdated",
                query = "select max(a.updated) from Area a")
})
@SuppressWarnings("unused")
public class Area extends VersionedEntity<Integer> implements ILocalizable<AreaDesc>, Comparable<Area> {

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

    /** Constructor */
    public Area() {
    }


    /** Constructor */
    public Area(AreaVo area) {
        updateArea(area);
    }


    /** Updates this area from the given area */
    public void updateArea(AreaVo area) {

        // NB: Ignore parent and children...
        this.id = area.getId();
        this.geometry = GeoJsonUtils.toJts(area.getGeometry());
        this.siblingSortOrder = area.getSiblingSortOrder();
        if (area.getDescs() != null) {
            area.getDescs().stream()
                    .forEach(desc -> createDesc(desc.getLang()).setName(desc.getName()));
        }
    }


    /** Converts this entity to a value object */
    public AreaVo toVo(DataFilter filter) {

        DataFilter compFilter = filter.forComponent(Area.class);

        AreaVo area = new AreaVo();
        area.setId(id);
        area.setSiblingSortOrder(siblingSortOrder);

        if (compFilter.includeGeometry()) {
            area.setGeometry(GeoJsonUtils.fromJts(geometry));
        }

        if (compFilter.includeChildren()) {
            children.forEach(child -> area.checkCreateChildren().add(child.toVo(compFilter)));
        }

        if (compFilter.includeParent() && parent != null) {
            area.setParent(parent.toVo(compFilter));
        } else if (compFilter.includeParentId() && parent != null) {
            AreaVo parent = new AreaVo();
            parent.setId(parent.getId());
            area.setParent(parent);
        }

        if (!descs.isEmpty()) {
            area.setDescs(descs.stream()
                .map(AreaDesc::toVo)
                .collect(Collectors.toList()));
        }
        return area;
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
        area.setSiblingSortOrder(lastChild == null ? Math.random() : lastChild.getSiblingSortOrder() + 10.0d);

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


    /** {@inheritDoc} */
    @Override
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


    /*************************/
    /** Getters and Setters **/
    /*************************/

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
}

