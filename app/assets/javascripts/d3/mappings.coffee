###
  The code for generating the mapping tree and editor.
###
Editor.CookbookresourcesMappingsView = Ember.View.extend({
  classNames: ['d3'],
  didInsertElement: ->
    controller = this.get('controller')
    dfasdls = controller.get('cookbook')
    if(dfasdls.target != null && dfasdls.sources.length > 0)
      drawPane(dfasdls, controller)
  mappingChanged: (->
    controller = this.get('controller')
    initializeMapping = controller.get("initializeMapping")
    if (initializeMapping > 0)
      redrawRecipes(controller)
  ).observes('controller.selectedMapping')
  elementsForDisplayChanged: (->
    controller = this.get('controller')
    dfasdls = controller.get('cookbook')
    changesElementsForDisplay = controller.get("changesElementsForDisplay")
    if(changesElementsForDisplay > 0)
      if(dfasdls.target != null && dfasdls.sources.length > 0)
        drawPane(dfasdls, controller)
  ).observes('controller.changesElementsForDisplay')
  recipeChanged: (->
    controller = this.get('controller')
    initializeMapping = controller.get("initializeMapping")
    if (initializeMapping > 0)
      redrawRecipes(controller)
  ).observes('controller.selectedRecipe')
  cookbookChanged: (->
    controller = this.get('controller')
    changes = controller.get('changes')
    if (changes > 0)
      redrawRecipes(controller)
  ).observes('controller.changes')
})

###
  Helper function to convert a given xml tree into json format because d3.js can't handle xml trees properly.
###
dfasdl2Json = (xml, dfasdlId, num = 0, level = 0) ->
  json = {}

  if (xml.tagName && xml.nodeType == Node.ELEMENT_NODE)
    json['tagName'] = xml.tagName
    json['dfasdlId'] = dfasdlId
    json['dfasdlNum'] = num
    json['level'] = level

    if (xml.attributes && xml.attributes.length > 0)
      for attribute in xml.attributes
        json[attribute.nodeName] = attribute.value
    else
      console.log('No attributes for ' + xml.tagName)
  else if (xml.nodeType == Node.DOCUMENT_NODE)
    json['tagName'] = 'xml'

  if (json.tagName && xml.hasChildNodes())
    json['children'] = []
    for child in xml.childNodes
      if (child.nodeType == Node.ELEMENT_NODE)
        json['children'].push(dfasdl2Json(child, dfasdlId, num, level + 1))

  json

###
  Draws the pane for the editor and calls the appropriate functions for drawing the dfasdl trees
  and their mappings.

  data       : The data.
  controller : The ember controller.
###
drawPane = (data, controller) ->
  widthPadding = 30
  heightPadding = 150
  document.getElementById("editor-pane").innerHTML = ""
  width = document.getElementById("editor-pane").offsetWidth - widthPadding
  height = $(document).height() - heightPadding
  geometry = { width: width, height: height }
  pane = d3.select('#editor-pane').append('svg').attr('width', width).attr('height', height).attr('class', 'overlay')
  baseGroup = pane.append('g').attr('id', 'base-group')
  zoom = -> baseGroup.attr("transform", "translate(" + d3.event.translate + ")scale(" + d3.event.scale + ")")
  zoomListener = d3.behavior.zoom().scaleExtent([0.1, 3]).on("zoom", zoom)
  pane.call(zoomListener)

  sourceWidth = 0
  targetWidth = 0

  centerTree = (source, mode = 'source') ->
    sourceTreeWidth = parseFloat(d3.select('#tensei-data-sourceTree').attr("tensei-data-width"))
    sourceTreeHeight = parseFloat(d3.select('#tensei-data-sourceTree').attr("tensei-data-height"))
    targetTreeWidth = parseFloat(d3.select('#tensei-data-targetTree').attr("tensei-data-width"))
    targetTreeHeight = parseFloat(d3.select('#tensei-data-targetTree').attr("tensei-data-height"))

    # Skalierung berechnen
    myScale = 1
    x = -(sourceTreeWidth + 2 * targetTreeWidth ) / 2
    y = 0
    myHeight = height - heightPadding
    myWidth = width

    if (sourceTreeHeight > height || targetTreeHeight > height)
      if (sourceTreeHeight > targetTreeHeight)
        myScale = parseFloat(height / sourceTreeHeight)
      else
        myScale = parseFloat(height / targetTreeHeight)
      if (myScale > 0.1)
        myScale = myScale - 0.01
      x = -myWidth
      myHeight = 0
    else if ((sourceTreeWidth + 2 * targetTreeWidth) > width)
      myScale = parseFloat(width / (sourceTreeWidth + 2 * targetTreeWidth))
      if (myScale > 0.1)
        myScale = myScale - 0.09

    scale = myScale
    x = x * scale + myWidth / 2
    y = y * scale + myHeight / 2

    d3.select('g').transition().duration(0).attr("transform", "translate(" + x + "," + y + ")scale(" + scale + ")")
    zoomListener.scale(scale)
    zoomListener.translate([x, y])

  selectedNodes = {
    source: null,
    target: null
  }

  clickLinkFn = (d) ->
    console.log('Click...')
    console.log(d)

  selectNodes = (node, mode) ->
    recipe = controller.get('selectedRecipe')
    if (recipe.id != null)
      if (node.children)
        console.log('Mapping of parent nodes not yet supported.')
      else
        switch mode
          when 'source' then selectedNodes.source =
            if (selectedNodes.source == node)
              nodeActive(node, true)
              null
            else
              nodeActive(selectedNodes.source, true)
              nodeActive(node, false)
              node
          when 'target' then selectedNodes.target =
            if (selectedNodes.target == node)
              nodeActive(node, true)
              null
            else
              nodeActive(selectedNodes.target, true)
              nodeActive(node, false)
              node
          else console.error('Illegal mode ' + mode)
        if (selectedNodes.source != null && selectedNodes.target != null)
          mappingTransformation =
            if (controller.get('selectedMapping') != null)
              controller.get('selectedMapping')
            else
              mappingKeyObject = EditorClasses.createMappingKeyFieldDefinition("")
              mt = EditorClasses.createMappingTransformation([], [], [], [], mappingKeyObject)
              controller.set('selectedMapping', mt)
              mt

          sourceElementReference = EditorClasses.createElementReference(selectedNodes.source.dfasdlId, selectedNodes.source.id)
          targetElementReference = EditorClasses.createElementReference(selectedNodes.target.dfasdlId, selectedNodes.target.id)

          if (recipe.mode == 'MapAllToAll')
            elementInSources = (i for i in mappingTransformation.sources when i.elementId is selectedNodes.source.id and i.dfasdlId is selectedNodes.source.dfasdlId)[0]
            if (elementInSources == undefined)
              mappingTransformation.sources.pushObject(sourceElementReference)
            elementInTargets = (i for i in mappingTransformation.targets when i.elementId is selectedNodes.target.id and i.dfasdlId is selectedNodes.target.dfasdlId)[0]
            if (elementInTargets == undefined)
              mappingTransformation.targets.pushObject(targetElementReference)
          else
            mappingTransformation.sources.pushObject(sourceElementReference)
            mappingTransformation.targets.pushObject(targetElementReference)

          drawMappingTransformation(mappingTransformation, selectedNodes.source, selectedNodes.target, mappingPathsGroup, recipeTooltipsGroup, clickLinkFn, controller)
          if (recipe.mode != "MapAllToAll")
            nodeActive(selectedNodes.source, true)
            selectedNodes.source = null
          nodeActive(selectedNodes.target, true)
          selectedNodes.target = null

  nodeActive = (node, isActive) ->
    if (node != undefined && node != null)
      if (isActive)
        d3.select("#"+createNodeHtmlId(node)).attr("class", "node")
      else
        d3.select("#"+createNodeHtmlId(node)).attr("class", "node nodeActive")

  clickFnSource = (d) ->
    if (!d3.event.defaultPrevented)
      selectNodes(d, 'source')
      #centerNode(d, 'source')

  clickFnTarget = (d) ->
    if (!d3.event.defaultPrevented)
      selectNodes(d, 'target')
      #centerNode(d, 'target')

  json = {
    tagName: 'sources',
    children: []
  }
  dfasdlNum = 0

  # Add a group for the mapping paths
  # This group must be added to the baseGroup BEFORE the trees are drawn. Otherwise,
  # the strokes of the paths will be over the circles.
  mappingPathsGroup = baseGroup.append('g').attr('id', 'mappingPaths')

  data.sources.forEach((source) ->
    json['children'].push(dfasdl2Json(source.content, source.id, dfasdlNum)['children'][0])
    dfasdlNum += 1
  )
  sourceData = drawSourceTree(controller, json, baseGroup, geometry, clickFnSource)
  sourceSize = sourceData['sourceSize']
  sourceGroup = sourceData['svgGroup']
  sourceWidth = sourceSize['width']
  sourceHeight = sourceSize['height']
  controller.get('dfasdls').sources = json

  targetJson = dfasdl2Json(data.target.content, data.target.id)['children'][0]
  targetData = drawTargetTree(controller, targetJson, baseGroup, geometry, clickFnTarget, sourceWidth)
  targetSize = targetData['targetSize']
  targetGroup = targetData['svgGroup']
  targetWidth = targetSize['width']
  targetHeight = targetSize['height']
  controller.get('dfasdls').target = targetJson

  baseGroup.append('span').attr('id', 'tensei-data-sourceTree').attr("tensei-data-height", sourceHeight).attr("tensei-data-width", sourceWidth)
  baseGroup.append('span').attr('id', 'tensei-data-targetTree').attr("tensei-data-height", targetHeight).attr("tensei-data-width", targetWidth)

  # This group contains the tooltips of the recipes and must be added AFTER the source and target tree
  # Otherwise, the simple tooltips of the source and target tree will block these ones.
  recipeTooltipsGroup = baseGroup.append('g').attr('id', 'recipeTooltips')

  changePosition(sourceGroup, sourceHeight, sourceWidth, targetGroup, targetHeight, targetWidth)

  baseGroup.x0 = geometry.height / 2
  baseGroup.y0 = geometry.width / 2

  recipes = controller.get('recipes')
  for recipe in recipes
    drawRecipe(recipe, controller, mappingPathsGroup, recipeTooltipsGroup, clickLinkFn)

  ### Increase the variable to stop the re-drawing of the mapping after reloading the page ###
  controller.set("initializeMapping", 1)
  centerTree(baseGroup)

redrawRecipes = (controller) ->
  mappingPathsGroup = d3.select('#mappingPaths')
  tooltipsGroup = d3.select('#recipeTooltips')

  mappingPathsGroup.selectAll('path.mapping').remove()
  mappingPathsGroup.selectAll('path.mappingSelected').remove()
  mappingPathsGroup.selectAll('path.currentMappingSelected').remove()
  tooltipsGroup.selectAll('circle.nodeTooltip').remove()

  clickLinkFn = (d) ->
    console.log('Click...')
    console.log(d)

  recipes = controller.get('recipes')
  for recipe in recipes
    drawRecipe(recipe, controller, mappingPathsGroup,tooltipsGroup, clickLinkFn)

###
 Adjust the position of the trees depending on the size of the source and the target tree.
 If the source tree is higher than the target tree, the target tree is moved to the middle of the height of the
 source tree. If the target tree is higher than the source tree, the source tree is moved to the middle of the
 target tree height.
###
changePosition = (sourceGroup, sourceHeight, sourceWidth, targetGroup, targetHeight, targetWidth) ->
  if (sourceHeight > targetHeight)
    targetGroup.attr('transform', () -> 'translate(' + (sourceWidth + targetWidth * 2) + ','+((sourceHeight - targetHeight) / 2)+')')
  else if (sourceHeight < targetHeight)
    sourceGroup.attr('transform', () -> 'translate(0,'+(targetHeight / 2)+')')
    targetGroup.attr('transform', () -> 'translate(' + (sourceWidth + targetWidth * 2) + ',0)')
  else
    targetGroup.attr('transform', () -> 'translate(' + (sourceWidth + targetWidth * 2) + ','+((sourceHeight - targetHeight) / 2)+')')


###
  Draw a line on the given base svg group to represent a mapping transformation.

  mappingTransformation : The data to bind to the d3 graph.
  sourceNode            : The source node.
  targetNode            : The target node.
  baseGroup             : The base svg group to draw upon.
  isSelected            : Whether this element should be marked as selected.
  clickLinkFn           : The function to execute upon a click on the link.
###
drawMappingTransformation = (mappingTransformation, sourceNode, targetNode, mappingsGroup, recipeTooltipsGroup, clickLinkFn, controller, lineClass = "mapping") ->
  id = 'mapping-' + sourceNode.id + '-of-' + sourceNode.dfasdlId + '-to-' + targetNode.id + '-of-' + targetNode.dfasdlId
  sourceTreeWidth = parseFloat(d3.select('#tensei-data-sourceTree').attr("tensei-data-width"))
  sourceTreeHeight = parseFloat(d3.select('#tensei-data-sourceTree').attr("tensei-data-height"))
  targetTreeWidth = parseFloat(d3.select('#tensei-data-targetTree').attr("tensei-data-width"))
  targetTreeHeight = parseFloat(d3.select('#tensei-data-targetTree').attr("tensei-data-height"))

  targetTreeMaxNodeLevel = parseFloat(d3.select('#tensei-data-targetTree-'+targetNode.dfasdlNum+'-maxNodeLevel').attr("max"))
  sourceTreeMaxNodeLevel = parseFloat(d3.select('#tensei-data-sourceTree-'+sourceNode.dfasdlNum+'-maxNodeLevel').attr("max"))

  sourceX = sourceNode.y
  sourceY = sourceNode.x
  # Die Verbindug zum Zielelement muss abhängig vom Level der Knoten berechnet werden, da wir normalerweise immer
  # von den Endknoten ausgegangen sind und nun auch hintere Knoten berücksichtigen müssen, die zwar Endknoten
  # sind aber nicht am äußersten Ende der Bäume dargestellt werden.
  if (targetNode.level < targetTreeMaxNodeLevel && sourceNode.level == sourceTreeMaxNodeLevel)
    targetX = sourceTreeWidth + (targetTreeMaxNodeLevel - targetNode.level) * targetNode.y + targetTreeWidth
  else if (targetNode.level == targetTreeMaxNodeLevel && sourceNode.level < sourceTreeMaxNodeLevel)
    targetX = sourceTreeWidth + (targetTreeMaxNodeLevel - targetNode.level) * targetNode.y + targetTreeWidth
  else if (targetNode.level < targetTreeMaxNodeLevel && sourceNode.level < sourceTreeMaxNodeLevel)
    targetX = sourceTreeWidth + (targetTreeMaxNodeLevel - targetNode.level) * targetNode.y + targetTreeWidth
  else
    targetX = sourceTreeWidth + targetNode.y
  targetY = targetNode.x

  if (sourceTreeHeight > targetTreeHeight)
    targetY = ((sourceTreeHeight - targetTreeHeight) / 2) + targetNode.x
  else if (sourceTreeHeight < targetTreeHeight)
    sourceY = targetTreeHeight / 2 + sourceNode.x

  lineData = [
    {x: sourceX, y: sourceY},
    {x: targetX, y: targetY}
  ]
  lineFn = d3.svg.line().x((d) -> d.x).y((d) -> d.y)
  mappingsGroup.selectAll('g.mappingPaths path#' + id).data([mappingTransformation]).enter()
  .append('path')
  .attr('id', id)
  .attr('class', lineClass)
  .attr('d', lineFn(lineData))
  .on('click', clickLinkFn)

  recipeTooltipsGroup.selectAll("g.recipeTooltips circle#"+id+sourceNode.id)
  .data([sourceNode]).enter().append("circle")
  .attr("cx", sourceX)
  .attr("cy", sourceY)
  .attr("r", 5)
  .attr("class", "nodeTooltip")
  .attr("fill", "transparent")
  .on("mouseover", ->
    createRecipeTooltip(sourceNode, id, controller)
  )
  .on('mouseout', ->
    d3.select("#editor-pane #tooltip-"+id+"-"+sourceNode.id).remove()
  )

  recipeTooltipsGroup.selectAll("g.recipeTooltips circle#"+id+targetNode.id)
  .data([targetNode]).enter().append("circle")
  .attr("cx", targetX)
  .attr("cy", targetY)
  .attr("fill", "transparent")
  .attr("class", "nodeTooltip")
  .attr("r", 5)
  .on("mouseover", ->
    createRecipeTooltip(targetNode, id, controller, "target")
  )
  .on('mouseout', ->
    d3.select("#editor-pane #tooltip-"+id+"-"+targetNode.id).remove()
  )

###
  Create a tooltip for the given node that contains information about the node and the mappings the node is
  involved.

  node       : The node that gets the tooltip.
  mappingId  : The ID of the mappingTransformation on the pane.
  controller : The controller object.
  mode       : "source" or "target" depending on the node in the DFASDL.
###
createRecipeTooltip = (node, mappingId, controller, mode="source") ->
  pane = d3.select("#editor-pane")
  tooltipDiv = pane.append("div").attr("id", "tooltip-"+mappingId+"-"+node.id)

  tooltipDiv.attr("class", "nodeToolTip")
  absoluteMousePos = d3.mouse(pane.node())
  tooltipDiv.style({
    left: (absoluteMousePos[0] + 10)+'px',
    top: (absoluteMousePos[1] - 40)+'px'
  })
  html = "<div class=\"title\">"
  html += node.id
  html += "</div>"
  html += "<div class=\"content\">"
  html += createNodeInformation(node)
  recipes = controller.get('recipes')
  html += createRecipeOverview(recipes, node, mode)
  html += "</div>"
  tooltipDiv.html(html)

###
  Create the overview of the source or target IDs that are mapped to the given searchId.

  recipes : The recipes of the current cookbook.
  nodeId  : The node of the source or the target.
  mode    : "source", if the searchId is from a sourceNode and "target", if the searchId is from a targetNode
###
createRecipeOverview = (recipes, node, mode) ->
  html = ""
  recipes.forEach((recipe) ->
    mappings = recipe.mappings
    mappingsHtml = ""
    mappings.forEach((mapping) ->
      if (mode == "source")
        if (EditorHelpers.elementExistsInElementReferences(node.id, node.dfasdlId, mapping.sources) != undefined)
          if (recipe.mode == "MapAllToAll")
            mapping.targets.forEach((reference) ->
              mappingsHtml += createMappingEntry(reference, mapping.transformations, mapping.atomicTransformations)
            )
          else
            position = 0
            mapping.sources.forEach((reference) ->
              if(reference.elementId == node.id && position < mapping.targets.length)
                mappingsHtml += createMappingEntry(mapping.targets[position], mapping.transformations, mapping.atomicTransformations)
              position += 1
            )
      else
        if (EditorHelpers.elementExistsInElementReferences(node.id, node.dfasdlId, mapping.targets) != undefined)
          if (recipe.mode == "MapAllToAll")
            mapping.sources.forEach((reference) ->
              mappingsHtml += createMappingEntry(reference, mapping.transformations, mapping.atomicTransformations)
            )
          else
            position = 0
            mapping.targets.forEach((reference) ->
              if(reference.elementId == node.id && position < mapping.sources.length)
                mappingsHtml += createMappingEntry(mapping.sources[position], mapping.transformations, mapping.atomicTransformations)
              position += 1
            )
    )

    if (mappingsHtml != "")
      html += "<div class=\"recipe\">" + recipe.id + "</div>"
      html += "<ul class=\"ids\">" + mappingsHtml + "</ul>"
  )
  html

###
  Create the html for a mapping with transformations.

  reference             : The reference of the connected element
  transformations       : General transformations to this mapping.
  atomicTransformations : Atomic transformations to this mapping.
###
createMappingEntry = (reference, transformations, atomicTransformations) ->
  html = "<li><i>" + reference.elementId + "</i> in <i>"+ reference.dfasdlId.substring(0, 25) + "</i>"
  if (transformations != undefined && transformations.length > 0)
    html += createTransformationsString(transformations, false)
  if (atomicTransformations != undefined && atomicTransformations.length > 0)
    html += createTransformationsString(atomicTransformations, true)
  html += "</li>"
  html

###
  Creates the html for the specific transformations.

  transformations         : The used transformations.
  isAtomicTransformations : Whether atomic or general transformations.
###
createTransformationsString = (transformations, isAtomicTransformations) ->
  html = "<br />"
  if (isAtomicTransformations)
    html += "A: "
  else
    html += "T: "
  transformerNames = ""
  transformations.forEach((transformation) ->
    splits = transformation.transformerClassName.split(".")
    if (splits.length > 0)
      transformerNames += splits[splits.length - 1] + ", "
  )
  if (transformerNames.length > 2)
    transformerNames = transformerNames.substring(0, transformerNames.length - 2)
    html += transformerNames
  html

drawRecipe = (recipe, controller, mappingsGroup, recipeTooltipsGroup, clickLinkFn) ->
  dfasdls = controller.get('dfasdls')
  selectedRecipe = controller.get('selectedRecipe')
  selectedMapping = controller.get('selectedMapping')
  isRecipeSelected = false
  isCurrentRecipeSelected = false
  if (selectedRecipe && selectedRecipe.id != null)
    isRecipeSelected = true

  if (isRecipeSelected && selectedRecipe.id == recipe.id)
    isCurrentRecipeSelected = true

  if ((isRecipeSelected && isCurrentRecipeSelected) || !isRecipeSelected)
    mode = recipe.mode
    for mappingTransformation in recipe.mappings
      lineClass = "mapping"
      if (isRecipeSelected && isCurrentRecipeSelected)
        lineClass = "mappingSelected"

      # MapAllToAll
      if (mode == "MapAllToAll")
        targetReferences = mappingTransformation.targets
        for sourceReference in mappingTransformation.sources
          sourceNode = searchTree(dfasdls.sources, sourceReference)
          if (sourceNode != null && sourceNode != undefined)
            for targetReference in targetReferences
              targetNode = searchTree(dfasdls.target, targetReference)
              if (targetNode != null && targetNode != undefined)
                if (selectedMapping != null && selectedMapping != undefined &&
                  selectedMapping['sources'] != undefined && selectedMapping['sources'] != null &&
                  sourceReference in selectedMapping['sources'] &&
                  selectedMapping['targets'] != undefined && selectedMapping['targets'] != null &&
                  targetReference in selectedMapping['targets'])
                    lineClass = "currentMappingSelected"

                drawMappingTransformation(mappingTransformation, sourceNode, targetNode, mappingsGroup, recipeTooltipsGroup, clickLinkFn, controller, lineClass)

      # MapOneToOne
      else
        position = 0
        targetReferences = mappingTransformation.targets
        for sourceReference in mappingTransformation.sources
          sourceNode = searchTree(dfasdls.sources, sourceReference)
          if (sourceNode != null && sourceNode != undefined)
            if (targetReferences[position])
              targetNode = searchTree(dfasdls.target, targetReferences[position])
              if (targetNode != null && targetNode != undefined)
                if (selectedMapping != null && selectedMapping != undefined &&
                  selectedMapping['sources'] != undefined && selectedMapping['sources'] != null &&
                  sourceReference in selectedMapping['sources'] &&
                  selectedMapping['targets'] != undefined && selectedMapping['targets'] != null &&
                  targetReferences[position] in selectedMapping['targets'])
                    lineClass = "currentMappingSelected"
                position++
                drawMappingTransformation(mappingTransformation, sourceNode, targetNode, mappingsGroup, recipeTooltipsGroup, clickLinkFn, controller, lineClass)

###
  Search the given tree for a matching node refernce.

  root               : The root node.
  nodeReference      : The reference of the node to search for.
  foundNodeReference : A helper variable to avoid loosing data in the recursion.
###
searchTree = (root, nodeReference, foundNodeReference) ->
  if(root != undefined && root.id == nodeReference.elementId && root.dfasdlId == nodeReference.dfasdlId)
    foundNodeReference = root
    foundNodeReference
  else if (root != undefined && root.children && foundNodeReference == undefined)
    for e in root.children
      foundNodeReference = searchTree(e, nodeReference, foundNodeReference)
  foundNodeReference

###
  Draw the tree containing the dfasdl sources.

  data      : The json tree containing the dfasdl sources.
  baseSvg   : An svg element that should be used as base.
  geometry  : The desired width and height.
  clickNode : The function that will be executed upon a click on a node.
###
drawSourceTree = (controller, data, baseSvg, geometry, clickNode) ->
  maxLabelLength = 0
  nodeDepthMultiply = 5
  sourceElementsDisabled = controller.get('sourceElementsDisabled')
  primaries = {}

  visit = (parent, visitFn, childrenFn) ->
    if (parent)
      visitFn(parent)
      children = childrenFn(parent)
      if (children)
        visit(child, visitFn, childrenFn) for child in children

  visit(data, (d) ->
    name = createNodeName(d)
    maxLabelLength = Math.max(name.length, maxLabelLength)
    # Collect the primary keys if we have a 'seq' element
    if(d.tagName == "seq" && d['db-primary-key'] != undefined)
      content = d['db-primary-key']
      keys = "#{content}".split ","
      vals = (e.replace(" ", "") for e in keys)
      primaries[d.id] = vals
  , (d) ->
    if (d.children && d.children.length > 0)
      d.children
    else
      null
  )

  svgGroup = baseSvg.append('g').attr('class', 'source')
  tree = d3.layout.tree()

  update = (source) ->
    height = 0
    width = 0
    treeData = {}
    undefinedIdCounter = 0
    levelWidth = [1]

    childCount = (level, n) ->
      if (n != undefined && n.children && n.children.length > 0)
        children = n.children
        newChildren = []
        if (levelWidth.length <= level + 1)
          levelWidth.push(0)

        for child in children
          if (child == undefined || child.id == undefined || child.dfasdlId == undefined || EditorHelpers.elementExistsInObject(child.id, child.dfasdlId, sourceElementsDisabled) == undefined)
            newChildren.push(child)

        n.children = newChildren
        levelWidth[level + 1] += newChildren.length

        for child in newChildren
          childCount(level + 1, child)

    childCount(0, source)
    newHeight = d3.max(levelWidth) * 25
    tree = tree.size([newHeight, baseSvg.width])
    diagonal = d3.svg.diagonal().projection((d) -> [d.y, d.x])
    nodes = tree.nodes(source).reverse()
    links = tree.links(nodes)

    nodes.forEach (node) ->
      node.y = (node.depth * maxLabelLength * nodeDepthMultiply)

    # we add the number of the DFASDL (position in the sources array) to the id
    # -> if there are nodes in the tree that have the same ID (different DFASDLs), the
    #    nodes would be overwritten and not shown correctly
    node = svgGroup.selectAll('g.node').data(nodes, (d) ->
      if (d.id)
        d.id + ":" + d.dfasdlNum
      else
        d.id = undefinedIdCounter++
        d.id + ":" + d.dfasdlNum
    )
    nodeEnter = node.enter().append('g').attr('class', 'node').attr('transform', () -> 'translate(' + source.y0 + ',' + source.x0 + ')').on('click', clickNode).
      attr("id", (d) ->
        createNodeHtmlId(d)
      )
    nodeEnter.append('circle')
    .attr('class', (d) ->
      if (d.children)
        'nodeCircle'
      else
        'nodeCircle leaf'
    )
    .attr('r', 0)

    nodeEnter.append('text')
    .attr('x', (d) ->
      if (d.children || d._children)
        -10
      else
        10
    )
    .attr('dy', (d) ->
      if ((d.children || d._children) && d.parent)
        '1em'
      else
        '.35em'
    )
    .attr('class', (d) ->
      if(d.s)
        'nodeTextSemantic'
      else
        'nodeText'
    )
    .attr('text-anchor', (d) ->
      if (d.children || d._children)
        'end'
      else
        'start'
    )
    .text((d) ->
      createNodeName(d)
    )

    # Append the DB Specifications like 'primary key', 'foreign key', 'unique' or 'auto-inc'
    nodes.forEach (node) ->
      if (node && node.depth > 0 && node.tagName != "elem" && node.tagName != "seq" && node.tagName != "fixseq" && node.tagName != "celem" && node.tagName != "choice")
        hasSpec = hasNodeSpec(node, primaries)
        if (hasSpec && (e for own e of hasSpec).length > 0)
          nodeId = createNodeHtmlId(node)
          currentNode = d3.select('#'+nodeId)
          createDbSpecOutput(currentNode, hasSpec, false)

    pane = d3.select("#editor-pane")
    node.select('circle.nodeCircle')
    .attr('r', 4.5)
    .on("mouseover", (d) ->
      createNodeTooltip(d, pane)
    )
    .on('mouseout', (d) ->
      d3.select("#editor-pane #tooltip-"+d.id+"-"+d.dfasdlNum+"-"+d.level).remove()
    )

    nodeUpdate = node.transition().duration(0).attr('transform', (d) -> 'translate(' + d.y + ',' + d.x + ')')
    nodeUpdate.select('text').style('fill-opacity', 1)

    link = svgGroup.selectAll('path.link').data(links, (d) -> d.target.id + "#" + d.target.dfasdlId)
    link.enter().insert('path', 'g').attr('d', (d) ->
      o = {
        x: source.x0,
        y: source.y0
      }
      diagonal({ source: o, target: o})
    )
    .attr('class', 'link')
    link.transition().attr('d', diagonal)
    link.exit().transition().attr('d', (d) ->
      o = {
        x: source.x,
        y: source.y
      }
      diagonal({ source: o, target: o })
    ).remove()

    nodes.forEach (node) ->
      node.x0 = node.x
      node.y0 = node.y
      height = Math.max(node.x, height)
      width = Math.max(node.y, width)
      if (node.dfasdlNum >= -1 && node.level)
        if (treeData[node.dfasdlNum.toString()])
          treeData[node.dfasdlNum.toString()] = Math.max(treeData[node.dfasdlNum.toString()], node.level)
        else
          treeData[node.dfasdlNum.toString()] = node.level

    for num, level of treeData
      baseSvg.append('span').attr('id', 'tensei-data-sourceTree-'+num+'-maxNodeLevel').attr("max", level)

    {width, height}

  root = data
  root.x0 = geometry.width / 2
  root.y0 = 0

  sourceSize = update(root)

  {sourceSize, svgGroup}

###
  Draw the tree containing the dfasdl target.

  data        : The json tree containing the dfasdl target.
  baseSvg     : An svg element that should be used as base.
  geometry    : The desired width and height.
  clickNode   : The function that will be executed upon a click on a node.
  sourceWidth : The width of the source tree that is used to move the target tree to avoid overlapping.
###
drawTargetTree = (controller, data, baseSvg, geometry, clickNode, sourceWidth) ->
  maxLabelLength = 0
  nodeDepthMultiply = 5
  targetElementsDisabled = controller.get('targetElementsDisabled')
  # variable to store the primary keys of the sequences
  primaries = {}

  visit = (parent, visitFn, childrenFn) ->
    if (parent)
      visitFn(parent)
      children = childrenFn(parent)
      if (children)
        visit(child, visitFn, childrenFn) for child in children

  visit(data, (d) ->
    name = createNodeName(d)
    maxLabelLength = Math.max(name.length, maxLabelLength)
    # Collect the primary keys if we have a 'seq' element
    if(d.tagName == "seq" && d['db-primary-key'] != undefined)
      content = d['db-primary-key']
      keys = "#{content}".split ","
      vals = (e.replace(" ", "") for e in keys)
      primaries[d.id] = vals
  , (d) ->
    if (d.children && d.children.length > 0)
      d.children
    else
      null
  )

  paneWidth = 0
  svgGroup = baseSvg.append('g').attr('class', 'target')

  #tree = d3.layout.tree().size([paneWidth, paneHeight])
  tree = d3.layout.tree()

  update = (source) ->
    height = 0
    width = 0
    targetMaxNodeLevel = 1
    undefinedIdCounter = 0
    treeNum = 0
    levelWidth = [1]

    childCount = (level, n) ->
      if (n != undefined && n.children && n.children.length > 0)
        children = n.children
        newChildren = []
        if (levelWidth.length <= level + 1)
          levelWidth.push(0)

        for child in children
          if (child == undefined || child.id == undefined || child.dfasdlId == undefined || EditorHelpers.elementExistsInObject(child.id, child.dfasdlId, targetElementsDisabled) == undefined)
            newChildren.push(child)

        n.children = newChildren
        levelWidth[level + 1] += newChildren.length

        for child in newChildren
          childCount(level + 1, child)

    childCount(0, source)
    newHeight = d3.max(levelWidth) * 25
    tree = tree.size([newHeight, baseSvg.width])
    diagonal = d3.svg.diagonal().projection((d) -> [- d.y, d.x])
    nodes = tree.nodes(source).reverse()
    links = tree.links(nodes)

    nodes.forEach (node) ->
      node.y = (node.depth * maxLabelLength * nodeDepthMultiply)

    node = svgGroup.selectAll('g.node').data(nodes, (d) ->
      if (d.id)
        d.id
      else
        d.id = undefinedIdCounter++
        d.id
    )
    nodeEnter = node.enter().append('g').attr('class', 'node').attr('transform', (d) -> 'translate(' + (paneWidth - source.y0) + ',' + source.x0 + ')').on('click', clickNode).
      attr("id", (d) ->
        createNodeHtmlId(d)
      )
    nodeEnter.append('circle')
    .attr('class', (d) ->
      if (d.children)
        'nodeCircle'
      else
        'nodeCircle leaf'
    )
    .attr('r', 0)

    nodeEnter.append('text')
    .attr('x', (d) ->
      if (d.children || d._children)
        10
      else
        -10
    )
    .attr('dy', (d) ->
      if ((d.children || d._children) && d.parent)
        '1em'
      else
        '.35em'
    )
    .attr('class', (d) ->
      if(d.s)
        'nodeTextSemantic'
      else
        'nodeText')
    .attr('text-anchor', (d) ->
      if (d.children || d._children)
        'start'
      else
        'end'
    )
    .text((d) ->
      createNodeName(d)
    )

    # Append the DB Specifications like 'primary key', 'foreign key', 'unique' or 'auto-inc'
    nodes.forEach (node) ->
      if (node && node.depth > 0 && node.tagName != "elem" && node.tagName != "seq" && node.tagName != "fixseq" && node.tagName != "celem" && node.tagName != "choice")
        hasSpec = hasNodeSpec(node, primaries)
        if (hasSpec && (e for own e of hasSpec).length > 0)
          nodeId = createNodeHtmlId(node)
          currentNode = d3.select('#'+nodeId)
          createDbSpecOutput(currentNode, hasSpec, true)

    pane = d3.select("#editor-pane")
    node.select('circle.nodeCircle')
    .attr('r', 4.5)
    .on("mouseover", (d) ->
      createNodeTooltip(d, pane)
    )
    .on('mouseout', (d) ->
      d3.select("#editor-pane #tooltip-"+d.id+"-"+d.dfasdlNum+"-"+d.level).remove()
    )

    nodeUpdate = node.transition().duration(0).attr('transform', (d) -> 'translate(' + (paneWidth - d.y) + ',' + d.x + ')')
    nodeUpdate.select('text').style('fill-opacity', 1)

    link = svgGroup.selectAll('path.link').data(links, (d) -> d.target.id + "#" + d.target.dfasdlId)
    link.enter().insert('path', 'g').attr('d', (d) ->
      o = {
        x: source.x0,
        y: source.y0
      }
      diagonal({ source: o, target: o})
    )
    .attr('class', 'link')
    link.transition().attr('d', diagonal)
    link.exit().transition().attr('d', (d) ->
      o = {
        x: source.x,
        y: source.y
      }
      diagonal({ source: o, target: o })
    ).remove()

    nodes.forEach (node) ->
      node.x0 = node.x
      node.y0 = node.y
      height = Math.max(node.x, height)
      width = Math.max(node.y, width)
      if (node.level)
        targetMaxNodeLevel = Math.max(node.level, targetMaxNodeLevel)
      if (node.dfasdlNum >= -1)
        treeNum = node.dfasdlNum

    baseSvg.append('span').attr('id', 'tensei-data-targetTree-'+treeNum+'-maxNodeLevel').attr("max", targetMaxNodeLevel)

    {width, height}

  root = data
  root.x0 = geometry.width / 2
  root.y0 = 0

  targetSize = update(root)

  {targetSize, svgGroup}

###
  Determine the special attributes at the specific `node` that
  stand for `primary key`, `foreign key`, `unique` and `auto-increment`.
  Return an object that contains values for the determined attributes.
###
hasNodeSpec = (node, primaries) ->
  res = {}
  if (node['db-foreign-key'] != undefined)
    res['fk'] = true

  if (node['db-auto-inc'] != undefined)
    res['ai'] = true

  if (node['unique'] != undefined)
    res['u'] = true

  parent = findParentSeq(node)
  if (parent != null && parent.id && primaries[parent.id])
    prims = primaries[parent.id]
    nodeDbName = getNodeDbName(node)
    if (prims && nodeDbName in prims)
      res['pk'] = true
  res

###
  Create elements for the visualization that are placed on the opposite
  position of the text of the provided `currentNode`.
  The specific attributes are:
  - primary key
  - foreign key
  - unique
  - auto-increment
  If `currentNode` has on of the attributes, all 4 elements are displayed near
  to the node. The active ones are made visible using the specific css classes.
###
createDbSpecOutput = (currentNode, hasSpec, positive) ->
  xPositionPKRect =
    if (positive == true)
      10
    else
      -20
  xPositionPKText =
    if (positive == true)
      11
    else
      -19
  xPositionFKRect =
    if (positive == true)
      22
    else
      - 32
  xPositionFKText =
    if (positive == true)
      23.5
    else
      -31
  xPositionURect =
    if (positive == true)
      34
    else
      -44
  xPositionUText =
    if (positive == true)
      37
    else
      -41
  xPositionAIRect =
    if (positive == true)
      46
    else
      -56
  xPositionAIText =
    if (positive == true)
      48
    else
      -54

  # Primary Key
  if (hasSpec['pk'])
    currentNode.append("rect")
    .attr('x', (d) -> xPositionPKRect).attr('y', (d) -> -5).attr('rx', (d) -> 3). attr("ry", (d) -> 3).attr('height', (d) -> 10).attr('width', (d) -> 10).attr("class", "db-spec-active")
    currentNode.append("text")
    .attr('x', (d) -> xPositionPKText).attr('y', (d) -> '.4em').text("PK").attr("class", "db-spec-text-active")
  else
    currentNode.append("rect")
    .attr('x', (d) -> xPositionPKRect).attr('y', (d) -> -5).attr('rx', (d) -> 3). attr("ry", (d) -> 3).attr('height', (d) -> 10).attr('width', (d) -> 10).attr("class", "db-spec-inactive")
    currentNode.append("text")
    .attr('x', (d) -> xPositionPKText).attr('y', (d) -> '.4em').text("PK").attr("class", "db-spec-text-inactive")
  # Foreign Key
  if (hasSpec['fk'])
    currentNode.append("rect")
    .attr('x', (d) -> xPositionFKRect).attr('y', (d) -> -5).attr('rx', (d) -> 3). attr("ry", (d) -> 3).attr('height', (d) -> 10).attr('width', (d) -> 10).attr("class", "db-spec-active")
    currentNode.append("text")
    .attr('x', (d) -> xPositionFKText).attr('y', (d) -> '.4em').text("FK").attr("class", "db-spec-text-active")
  else
    currentNode.append("rect")
    .attr('x', (d) -> xPositionFKRect).attr('y', (d) -> -5).attr('rx', (d) -> 3). attr("ry", (d) -> 3).attr('height', (d) -> 10).attr('width', (d) -> 10).attr("class", "db-spec-inactive")
    currentNode.append("text")
    .attr('x', (d) -> xPositionFKText).attr('y', (d) -> '.4em').text("FK").attr("class", "db-spec-text-inactive")
  # Unique
  if (hasSpec['u'])
    currentNode.append("rect")
    .attr('x', (d) -> xPositionURect).attr('y', (d) -> -5).attr('rx', (d) -> 3). attr("ry", (d) -> 3).attr('height', (d) -> 10).attr('width', (d) -> 10).attr("class", "db-spec-active")
    currentNode.append("text")
    .attr('x', (d) -> xPositionUText).attr('y', (d) -> '.4em').text("U").attr("class", "db-spec-text-active")
  else
    currentNode.append("rect")
    .attr('x', (d) -> xPositionURect).attr('y', (d) -> -5).attr('rx', (d) -> 3). attr("ry", (d) -> 3).attr('height', (d) -> 10).attr('width', (d) -> 10).attr("class", "db-spec-inactive")
    currentNode.append("text")
    .attr('x', (d) -> xPositionUText).attr('y', (d) -> '.4em').text("U").attr("class", "db-spec-text-inactive")
  # Auto-Increment
  if (hasSpec['ai'])
    currentNode.append("rect")
    .attr('x', (d) -> xPositionAIRect).attr('y', (d) -> -5).attr('rx', (d) -> 3). attr("ry", (d) -> 3).attr('height', (d) -> 10).attr('width', (d) -> 10).attr("class", "db-spec-active")
    currentNode.append("text")
    .attr('x', (d) -> xPositionAIText).attr('y', (d) -> '.4em').text("AI").attr("class", "db-spec-text-active")
  else
    currentNode.append("rect")
    .attr('x', (d) -> xPositionAIRect).attr('y', (d) -> -5).attr('rx', (d) -> 3). attr("ry", (d) -> 3).attr('height', (d) -> 10).attr('width', (d) -> 10).attr("class", "db-spec-inactive")
    currentNode.append("text")
    .attr('x', (d) -> xPositionAIText).attr('y', (d) -> '.4em').text("AI").attr("class", "db-spec-text-inactive")

###
  Create the database information that is displayed near the element
  It contains: Primary key, foreign key, auto-increment and unique
###
createDbSpec = (node, primaries) ->
  html = ""
  specs = 0

  parent = findParentSeq(node)
  if (parent != null && parent.id)
    p = primaries[parent.id]
    nodeName = getNodeDbName(node)
    if (p && nodeName in p)
      specs += 1
      html += '<span class="db-spec-primary-key">PK</span>'
    else
      html += '<span class="db-spec-primary-key-no">PK</span>'
  else
    html += '<span class="db-spec-primary-key-no">PK</span>'

  if (node['db-foreign-key'] != undefined)
    specs += 1
    html += '<span class="db-spec-foreign-key">FK</span>'
  else
    html += '<span class="db-spec-foreign-key-no">FK</span>'

  if (node['db-auto-inc'] != undefined)
    specs += 1
    html += '<span class="db-spec-auto-inc">AI</span>'
  else
    html += '<span class="db-spec-auto-inc-no">AI</span>'

  if (node['unique'] != undefined)
    specs += 1
    html += '<span class="db-spec-unique">U</span>'
  else
    html += '<span class="db-spec-unique-no">U</span>'

  if (specs < 1)
    html = ""
  else
    html = '<span class="db-spec">'+html+'</span>'

  html

findParentSeq = (node) ->
  if (node)
    if(node.parent)
      if (node.parent.tagName == "seq")
        node.parent
      else
        findParentSeq(node.parent)
    else
      null
  else
    null

###
  Create a unique ID that will be placed to the node into the HTML.
###
createNodeHtmlId = (node) ->
  node.id + "-" + node.dfasdlNum + "-" + node.level + "-" + Math.round(node.x) + "-" + Math.round(node.y)

###
  Create the name of the node that is visible to the user.
  We try to display the shortest but still understandable name of the node.
###
createNodeName = (node) ->
  node.tagName + ' : ' + getNodeName(node)

###
  Get the name for the node that is used in the database as column name.
###
getNodeDbName = (node) ->
  if (node['db-column-name'])
    node['db-column-name']
  else
    node.id

# Determine the name of the node that will be shown in the mapping
# @param node The node of the tree
# @return The name for display
getNodeName = (node) ->
  if(node.s)
    node.s
  else if (node['db-column-name'])
    node['db-column-name']
  else if (node.parent && node.parent.id && node.id && typeof node.id == 'string')
    toReplace = ""+node.parent.id+"_"
    node.id.replace(toReplace, "")
  else
    if (node.id == undefined)
      if (node.dfasdlId != undefined)
        node.dfasdlId
      else
        node.level
    else
      node.id

createNodeTooltip = (node, pane) ->
  tooltipDiv = pane.append("div").attr("id", "tooltip-"+node.id+"-"+node.dfasdlNum+"-"+node.level)
  tooltipDiv.attr("class", "nodeToolTip")
  absoluteMousePos = d3.mouse(pane.node())
  tooltipDiv.style({
    left: (absoluteMousePos[0] + 10)+'px',
    top: (absoluteMousePos[1] - 40)+'px'
  })
  html = "<div class=\"title\">"
  if (node.level == 1)
    html += node.dfasdlId
  else
    html += node.id
  html += "</div>"
  html += "<div class=\"content\">"
  html += createNodeInformation(node)
  html += "</div>"

  tooltipDiv.html(html)

createNodeInformation = (node) ->
  html = "<div class=\"information\">"
  html += "<div><span class=\"key\">type</span>: " + node.tagName + "</div>"
  if(node.dfasdlId)
    html += "<div><span class=\"key\">DFASDL</span>: " + node.dfasdlId + "</div>"
  if(node["db-column-name"])
    html += "<div><span class=\"key\">db column</span>: " + node["db-column-name"] + "</div>"
  if(node.s)
    html += "<div><span class=\"key\">semantic</span>: " + node.s + "</div>"
  html += "</div>"
  html