package com.fundynamic.d2tm.game.rendering.gui.battlefield;

import com.fundynamic.d2tm.game.controls.Mouse;
import com.fundynamic.d2tm.game.controls.battlefield.*;
import com.fundynamic.d2tm.game.entities.*;
import com.fundynamic.d2tm.game.map.Cell;
import com.fundynamic.d2tm.game.map.Map;
import com.fundynamic.d2tm.game.rendering.gui.GuiElement;
import com.fundynamic.d2tm.game.types.EntityData;
import com.fundynamic.d2tm.math.Coordinate;
import com.fundynamic.d2tm.math.MapCoordinate;
import com.fundynamic.d2tm.math.Rectangle;
import com.fundynamic.d2tm.math.Vector2D;
import org.newdawn.slick.Color;
import org.newdawn.slick.Graphics;
import org.newdawn.slick.Image;
import org.newdawn.slick.SlickException;

import java.util.List;

import static com.fundynamic.d2tm.game.map.Cell.DOUBLE_TILE_SIZE;

/**
 * <p>
 * The battlefield is a view on the map which can be interacted with.
 * </p>
 *
 */
public class BattleField extends GuiElement implements CellBasedMouseBehavior, EntityEventsListener {

    private final RenderQueue renderQueue;

    // DATA RELATED
    private Map map;
    private Mouse mouse;

    private final EntityRepository entityRepository;

    // RENDERING RELATED
    private Vector2D viewingVector;

    private final Image buffer;

    private final CellTerrainRenderer cellTerrainRenderer;
    private final CellShroudRenderer cellShroudRenderer;
    private final CellViewportRenderer cellViewportRenderer;

    // MOVEMENT RELATED
    private final Rectangle viewingVectorPerimeter;
    private Vector2D velocity;
    private float moveSpeed;

    // MOUSE BEHAVIOR RELATED
    private AbstractBattleFieldMouseBehavior mouseBehavior;
    private Graphics bufferGraphics;

    public BattleField(Vector2D viewportDimensions,
                       Vector2D drawingPosition,
                       Vector2D viewingVector,
                       Map map,
                       Mouse mouse,
                       float moveSpeed,
                       Player player,
                       Image buffer,
                       EntityRepository entityRepository) throws SlickException {
        super(drawingPosition.getXAsInt(), drawingPosition.getYAsInt(), viewportDimensions.getXAsInt(), viewportDimensions.getYAsInt());

        this.map = map;
        this.mouse = mouse;
        this.entityRepository = entityRepository;

        this.mouseBehavior = new NormalMouse(this, getHoverCell());

        this.viewingVectorPerimeter = map.createViewablePerimeter(viewportDimensions);
        this.velocity = Vector2D.zero();

        this.moveSpeed = moveSpeed;

        this.viewingVector = viewingVector;
        this.cellViewportRenderer = new CellViewportRenderer(map, viewportDimensions);
        this.cellTerrainRenderer = new CellTerrainRenderer();
        this.cellShroudRenderer = new CellShroudRenderer(player, map.getShroud());

        this.buffer = buffer;
        this.bufferGraphics = buffer.getGraphics();

        this.renderQueue = new RenderQueue(this.viewingVector);
    }

    @Override
    public void render(Graphics graphics) {
        try {
            // TODO: Merge the culling into this viewport class(?)

            // render terrain
            cellViewportRenderer.render(bufferGraphics, viewingVector, cellTerrainRenderer);

            // draw entities
            renderQueue.render(bufferGraphics);

            // Render shroud
            cellViewportRenderer.render(bufferGraphics, viewingVector, cellShroudRenderer);

            // Draw mouse
            mouseBehavior.render(bufferGraphics);

            if (hasFocus) {
                bufferGraphics.setColor(Color.red);
                // we use 0,0, because the buffer we draw to is already being drawn
                // at the 'topleft' coordinate
                float lineWidth = bufferGraphics.getLineWidth();
                bufferGraphics.setLineWidth(2.0f);
                bufferGraphics.drawRect(1, 1, getWidth()-2, getHeight()-2);
                bufferGraphics.setLineWidth(lineWidth);
            }

            // Debugging info?

            drawBufferToGraphics(graphics, getTopLeft());
        } catch (SlickException e) {
            e.printStackTrace();
        }
    }

    public void update(float delta) {
        Vector2D translation = velocity.scale(delta);
        viewingVector = viewingVectorPerimeter.makeSureVectorStaysWithin(viewingVector.add(translation));
        renderQueue.updateCameraPosition(viewingVector);

        renderQueue.clear();

        Rectangle rectangle =
                Rectangle.createWithDimensions(
                        viewingVector.min(
                                Vector2D.create(DOUBLE_TILE_SIZE, DOUBLE_TILE_SIZE)
                        ),
                        Vector2D.create(getWidth() + DOUBLE_TILE_SIZE, getHeight() + DOUBLE_TILE_SIZE)
                );
        List<Entity> entitiesWithinViewport = entityRepository.findEntitiesWithinRectangle(rectangle).toList();

        renderQueue.put(entitiesWithinViewport);
    }

    public void moveLeft() {
        this.velocity = Vector2D.create(-moveSpeed, this.velocity.getY());
    }

    public void moveRight() {
        this.velocity = Vector2D.create(moveSpeed, this.velocity.getY());
    }

    public void moveUp() {
        this.velocity = Vector2D.create(this.velocity.getX(), -moveSpeed);
    }

    public void moveDown() {
        this.velocity = Vector2D.create(this.velocity.getX(), moveSpeed);
    }

    public void stopMovingHorizontally() {
        this.velocity = Vector2D.create(0, this.velocity.getY());
    }

    public void stopMovingVertically() {
        this.velocity = Vector2D.create(this.velocity.getX(), 0);
    }

    private void drawBufferToGraphics(Graphics graphics, Vector2D drawingVector) {
        graphics.drawImage(buffer, drawingVector.getX(), drawingVector.getY());
    }

    public Coordinate getAbsoluteCoordinateTopLeftOfTarget(EntityData entityDataToPlace, Vector2D mouseCoordinates) {
        // first get absolute viewport coordinates, we can calculate on the battlefield with that
        Coordinate viewportCoordinate = translateScreenToViewportCoordinate(mouseCoordinates);

        // now substract half of the structure to place, so we make the structure to place center beneath the mouse
        Vector2D halfDimensions = entityDataToPlace.halfDimensions();
        Coordinate topLeftOfEntity = viewportCoordinate.min(halfDimensions);

        Cell topLeftCellOfEntity = getCellByAbsoluteViewportCoordinate(topLeftOfEntity);
        return topLeftCellOfEntity.getCoordinate();
    }


    /**
     * <p>
     *     This method takes an absoluteMapCoordinate and translates this into a Viewport coordinate. This means
     *     effectively that any given coordinate is subtracted by the camera(viewport) position.
     * </p>
     * <h2>Usage</h1>
     * <p>
     *     Example: You want to draw anything on screen. Pass in the absolute coordinates here, and use the
     *     resulting Coordinate as vector to draw within the viewport.
     * </p>
     * @param absoluteMapCoordinate
     * @return
     */
    public Coordinate translateAbsoluteMapCoordinateToViewportCoordinate(Coordinate absoluteMapCoordinate) {
        return absoluteMapCoordinate.min(viewingVector);
    }

    /**
     * <p>A short-hand for {@link #translateAbsoluteMapCoordinateToViewportCoordinate(Coordinate)} but for {@link MapCoordinate}</p>
     * @param mapCoordinate
     * @return
     */
    public Coordinate translateMapCoordinateToViewportCoordinate(MapCoordinate mapCoordinate) {
        return translateAbsoluteMapCoordinateToViewportCoordinate(mapCoordinate.toCoordinate());
    }

    /**
     * <p>
     *     Given an absolute viewport coordinate (acquire one via {@link #translateScreenToViewportCoordinate(Vector2D)}, returns
     *     an absolute <em>map</em> {@link Coordinate}.
     * </p>
     * @param positionOnViewport
     * @return
     */
    public Coordinate translateViewportCoordinateToAbsoluteMapCoordinate(Vector2D positionOnViewport) {
        return new Coordinate(positionOnViewport.add(viewingVector));
    }

    /**
     * <p>
     * Given a Vector that represents a screen(/mouse) coordinate. The coordinate will be corrected into the
     * viewport (of this battlefield class). For instance: assuming the viewport is on 10,20 drawn on the SCREEN. A given
     * 10,20 coordinate will result in a 0,0 absolute coordinate. A screen coordinate of 20, 30 results in 0,10.
     *</p>
     * <b>Can return NULL when coordinate is <em>not</em> within the dimensions of this Gui Element!</b>
     * @param screenPosition
     * @return
     */
    public Coordinate translateScreenToViewportCoordinate(Vector2D screenPosition) {
        if (!isVectorWithin(screenPosition)) return null; // outside our viewport!
        return new Coordinate(screenPosition.min(getTopLeft()));
    }

    @Override
    public void leftClicked() {
        mouseBehavior.leftClicked();
    }

    @Override
    public void rightClicked() {
        mouseBehavior.rightClicked();
    }

    @Override
    public void draggedToCoordinates(Vector2D coordinates) {
        int newX = coordinates.getXAsInt();
        int newY = coordinates.getYAsInt();

        Vector2D viewportCoordinates = translateScreenToViewportCoordinate(coordinates);

        if (viewportCoordinates == null) {
            Vector2D drawingVector = getTopLeft();
            Vector2D viewportDimensions = getDimensions();

            int snappedX = Math.min(
                    Math.max(newX, drawingVector.getXAsInt()),
                    viewportDimensions.getXAsInt() + drawingVector.getXAsInt()
            );

            int snappedY = Math.min(
                    Math.max(newY, drawingVector.getYAsInt()),
                    viewportDimensions.getYAsInt() + drawingVector.getYAsInt()
            );

            Vector2D snappedCoordinates = Vector2D.create(snappedX, snappedY);
            viewportCoordinates = translateScreenToViewportCoordinate(snappedCoordinates);
        }

        mouseBehavior.draggedToCoordinates(viewportCoordinates);
    }

    @Override
    public void movedTo(Vector2D coordinates) {
//        System.out.println("BattleField: movedTo " + coordinates + " MouseBehavior: " + mouseBehavior);
        this.mouseBehavior.movedTo(coordinates);
        Cell cellByScreenCoordinate = getCellByScreenCoordinate(coordinates);
//        System.out.println("BattleField: movedTo, cellByScreenCoordinate : " + cellByScreenCoordinate);
        mouseMovedToCell(cellByScreenCoordinate);
    }

    /**
     * Returns a Cell based on the screen coordinate.
     *
     * @param coordinates
     * @return
     */
    public Cell getCellByScreenCoordinate(Vector2D coordinates) {
        Coordinate absoluteViewportCoordinate = translateScreenToViewportCoordinate(coordinates);
        return getCellByAbsoluteViewportCoordinate(absoluteViewportCoordinate);
    }

    /**
     * <p>
     * Given an absolute <em>viewport</em> coordinate (you can acquire one by calling {@link #translateScreenToViewportCoordinate(Vector2D)}, this method
     * figures out which map coordinate is underneath and what cell is associated with it. Returns the found cell.
     * </p>
     * @param absoluteViewportCoordinate
     * @return
     */
    public Cell getCellByAbsoluteViewportCoordinate(Coordinate absoluteViewportCoordinate) {
        Coordinate absoluteMapCoordinate = translateViewportCoordinateToAbsoluteMapCoordinate(absoluteViewportCoordinate);
        return map.getCellByAbsoluteMapCoordinates(absoluteMapCoordinate);
    }

    @Override
    public void leftButtonReleased() {
        this.mouseBehavior.leftButtonReleased();
    }

    @Override
    public void mouseMovedToCell(Cell cell) {
        this.mouseBehavior.mouseMovedToCell(cell);
    }

    public EntityRepository getEntityRepository() {
        return entityRepository;
    }

    public Mouse getMouse() {
        return mouse;
    }

    public void setMouseBehavior(AbstractBattleFieldMouseBehavior mouseBehavior) {
        // want to change to dragging selection box thingy
        if (mouseBehavior.getClass().equals(DraggingSelectionBoxMouse.class)) {
            if (!this.mouseBehavior.getClass().equals(NormalMouse.class)) {
                // not a normal mouse, so we cannot transform to it
                return;
            }
        }
        this.mouseBehavior = mouseBehavior;
    }

    public AbstractBattleFieldMouseBehavior getMouseBehavior() {
        return mouseBehavior;
    }

    public Cell getHoverCell() {
        if (mouseBehavior == null) return null;
        return mouseBehavior.getHoverCell();
    }

    public void setViewingVector(Vector2D viewingVector) {
        this.viewingVector = viewingVectorPerimeter.makeSureVectorStaysWithin(viewingVector);
    }

    @Override
    public void entitiesSelected(EntitiesSet entities) {
        System.out.println("Battlefield gets told that " + entities + " are selected");

        EntitiesSet entityBuildersForControllingPlayer = entities.filter(
                Predicate.builder()
                        .belongsToPlayer(mouse.getControllingPlayer())
        );

        if (entityBuildersForControllingPlayer.hasOne()) {
            Entity first = entities.getFirst();
            guiComposite.entitySelected(first);
        } else {
            guiComposite.allEntityBuildersDeSelected();
        }
    }

    @Override
    public void entitiesDeselected(EntitiesSet entities) {
        System.out.println("Battlefield gets told that " + entities + " are de-selected");
    }

    // These methods are here mainly for (easier) testing. Best would be to remove them if possible - and at the very
    // least not to use them in the non-test code.
    public Vector2D getViewingVector() {
        return viewingVector;
    }

    public void centerViewportOn(MapCoordinate centerCoordinate) {
        Vector2D viewportDimensions = this.getViewportCellBoundaries().getDimensions();
        Vector2D viewingVector = centerCoordinate
            .add(viewportDimensions.scale(-.5f)) // convert it to the center of the viewport
            .toCoordinate();
        setViewingVector(viewingVector);
    }

    public Rectangle getViewportCellBoundaries() {
        return this.cellViewportRenderer.getViewport(this.viewingVector);
    }

    public Map getMap() {
        return this.map;
    }

    /**
     * Event: An entity is placed on the map. The Battlefield propagates that to the guiComposite
     * @param entity
     */
    public void entityPlacedOnMap(Entity entity) {
        guiComposite.entityPlacedOnMap(entity);
    }

    @Override
    public String toString() {
        return "BattleField @";
    }
}
