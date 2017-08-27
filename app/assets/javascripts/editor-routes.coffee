###
  Routes for the editor application.
  Here we handle the loading and saving of resources and the preparation of the controllers.
###

Editor.Router.map(->
  this.resource('cookbookresources', {path : '/:id'}, ->
    this.route('mappings')
    this.route('resources')
    this.route('settings')
  )
)

Editor.CookbookresourcesRoute = Ember.Route.extend({
  model: (params) -> this.store.find('cookbookresource', params.id)
  setupController: (controller, model) -> controller.set('model', model)
})

Editor.CookbookresourcesMappingsRoute = Ember.Route.extend({
  model: ->
    Ember.RSVP.hash({
      cookbookresource: this.modelFor('cookbookresources'),
      dfasdlresources: this.store.findAll('dfasdlresource')
    })

  setupController: (controller, model) ->
    controller.set('model', model)
    recipes = []
    sequenceIdsList = []
    cookbook = model.cookbookresource.get('cookbook')
    cookbook.recipes.map (recipe) ->
      recipes.push(recipe)

    sources = cookbook.sources
    sourceIds = []
    sources.forEach((source) ->
      sourceIds = EditorHelpers.getDFASDLIds(source.id, source.content, sourceIds)
      sequenceIdsList.push({dfasdlId: source.id, elementIds: EditorHelpers.getDFASDLIdsSequences(source.content, new Set())})
    )

    mappingKeys = [""]
    # Just the elementIds from the source DFASDLs
    sourceIds.forEach((sourceId) ->
      if (!mappingKeys.contains(sourceId.elementId))
        mappingKeys.push(sourceId.elementId)
    )

    sourceIdsSequences = []
    sourceElementsDisabled = controller.get('sourceElementsDisabled')
    sequenceIdsList.forEach((sequence) ->
      elements = sequence.elementIds
      # if the `sequence` name is in the list of disabled elements, we set `selected` to `false`
      # necessary if the user switches from the `Mappings` to `Settings` or `Resources`
      elements.forEach((element) ->
        if (sourceElementsDisabled != undefined && EditorHelpers.elementExistsInObject(element, sequence.dfasdlId, sourceElementsDisabled) != undefined)
          entry = EditorClasses.createElementToDisplay(element, sequence.dfasdlId, false)
        else
          entry = EditorClasses.createElementToDisplay(element, sequence.dfasdlId, true)
        sourceIdsSequences.pushObject(entry)
      )
    )

    target = cookbook.target
    targetIdsSequences = []
    if (target)
      sequenceIdsList = []
      sequenceIdsList.push({dfasdlId: target.id, elementIds: EditorHelpers.getDFASDLIdsSequences(target.content, new Set())})
      targetElementsDisabled = controller.get('targetElementsDisabled')
      sequenceIdsList.forEach((sequence) ->
        elements = sequence.elementIds
        # if the `sequence` name is in the list of disabled elements, we set `selected` to `false`
        # necessary if the user switches from the `Mappings` to `Settings` or `Resources`
        elements.forEach((element) ->
          if (targetElementsDisabled != undefined && EditorHelpers.elementExistsInObject(element, sequence.dfasdlId, targetElementsDisabled) != undefined)
            entry = EditorClasses.createElementToDisplay(element, sequence.dfasdlId, false)
          else
            entry = EditorClasses.createElementToDisplay(element, sequence.dfasdlId, true)
          targetIdsSequences.pushObject(entry)
        )
      )

    controller.set('recipes', recipes)
    controller.set('availableMappingKeys', mappingKeys)
    controller.set('availableSourceIds', sourceIds)
    controller.set('availableSourceElementsForDisplay', sourceIdsSequences)
    controller.set('availableTargetElementsForDisplay', targetIdsSequences)
    controller.set('selectedMapping', {})
    controller.set('selectedRecipe', { id: null })
    controller.set('selectedAtomicTransformation', null)
    controller.set('selectedAtomicTransformer', null)
    controller.set('selectedTransformation', null)
    controller.set('selectedTransformer', null)
    controller.set('dfasdls', {})
    controller.set('changes', 0)
    controller.set('changesElementsForDisplay', 0)
    controller.set('initializeMapping', 0)
    controller.set('mappingsSaving', 0)
    controller.set('saved', 0)

  actions: {
    saveMappings: ->
      controller = this.controllerFor('cookbookresourcesMappings')
      controller.set('saveFailure', 0)
      controller.set('mappingsSaving', 1)
      selectedMapping = controller.get('selectedMapping')
      if(selectedMapping != null)
        sources = []
        $('#sortableSourceIds').find('.item').each(() ->
          sourceElementReference = EditorClasses.createElementReference($(this).data('dfasdlId'), $(this).data('id'))
          sources.push(sourceElementReference)
        )
        selectedMapping.sources = sources

        targets = []
        $('#sortableTargetIds').find('.item').each(() ->
          targetElementReference = EditorClasses.createElementReference($(this).data('dfasdlId'), $(this).data('id'))
          targets.push(targetElementReference)
        )
        selectedMapping.targets = targets

      increasedRecipeNumber = controller.get('newRecipeNumber') + 1
      controller.set('newRecipeNumber', increasedRecipeNumber)
      cookbookresource = this.currentModel.cookbookresource
      myRecipes = controller.get('recipes')
      recipes = myRecipes.map (myRecipe) -> EditorClasses.createRecipe(myRecipe.id, myRecipe.mode, myRecipe.mappings)
      cookbookresource.get('cookbook').recipes = recipes
      onSuccess = (cookbookresource) ->
        console.log('Mappings saved.')
        controller.set('selectedRecipe', { id: null })
        controller.set('selectedMapping', null)
        controller.set('mappingsSaving', 0)
        controller.set('changes', 0)
        controller.set('saved', 1)

      onFailure = (failure) ->
        console.error(failure.message)
        controller.set('mappingsSaving', 0)
        controller.set('saveFailure', 1)

      cookbookresource.save().then(onSuccess, onFailure)
  }
})

Editor.CookbookresourcesResourcesRoute = Ember.Route.extend({
  model: ->
    Ember.RSVP.hash({
      cookbookresource: this.modelFor('cookbookresources'),
      dfasdlresources: this.store.findAll('dfasdlresource')
    })

  setupController: (controller, model) ->
    controller.set('model', model)
    controller.set('nameError', false)
    controller.set('duplicateError', false)
    cookbook = model.cookbookresource.get('cookbook')

    if (controller.get('name') == '')
      controller.set('name', cookbook.id)
    if (controller.get('selectedTarget') == null && cookbook.target != null)
      t = EditorHelpers.findDfasdlResource(cookbook.target.id, model.dfasdlresources)
      if (t != null)
        controller.set('selectedTarget', t)
    s = EditorHelpers.findDfasdlResources(cookbook.sources, model.dfasdlresources)
    if (s != null && s.length > 0)
      sourceIds = (d.id for d in s)
      controller.set('selectedSources', sourceIds)
    controller.set('resourcesSaved', 0)
    controller.set('resourceSaving', 0)

  actions: {
    saveResources: ->
      controller = this.controllerFor('cookbookresourcesResources')
      controller.set('resourceSaving', 1)
      cookbookresource = this.currentModel.cookbookresource

      dfasdlresources = this.currentModel.dfasdlresources
      cookbook = cookbookresource.get('cookbook')
      cookbookName = controller.get('name')
      # Check whether the name of the cookbook is already used by another cookbook
      cookbookExists = EditorHelpers.cookbookExists(cookbookName, cookbookresource.id)
      cookbookExists.then((result) ->
        controller.set("duplicateError", false)
        controller.set("nameError", false)
        if(result == false)
          if (cookbookName.match(/^[a-zA-Z0-9-]+$/g))
            cookbook.id = cookbookName
            # Get the selected target version from the controller and load it from the server if neccessary.
            selectedTarget = controller.get('selectedTarget')
            target = EditorHelpers.loadDfasdl(selectedTarget.id, selectedTarget.get('dfasdl').version)
            target.then((result) ->
              if (cookbook.target == null || cookbook.target.content == "" || cookbook.target.id != selectedTarget.id)
                cookbook.target = result
            ).then(->
              # Loop through the sources from the controller and prepare them for loading if neccessary.
              sourceIdVersions = {}
              # This nasty code is necessary because we must store the id for a multi-select box but will get the objects if the user changed values in the select box.
              selectedSourceIds = controller.get('selectedSources').map (s) ->
                if (typeof(s) == "string")
                  s
                else
                  s.id
              selectedSources = selectedSourceIds.map (id) -> dfasdlresources.findBy('id', id).get('dfasdl')
              selectedSources.forEach (source) -> sourceIdVersions[source.id] = source.version
              promises = selectedSources.map (source) ->
                resource = EditorHelpers.findDfasdlResource(source.id, dfasdlresources)
                EditorHelpers.loadDfasdl(resource.id, sourceIdVersions[source.id])
              Ember.RSVP.all(promises).then((results) -> cookbook.sources = results).then(->
                onSuccess = (cookbookresource) ->
                  controller.set('resourcesSaved', 1)
                  console.log('Resources saved.')
                  controller.set('resourceSaving', 0)

                onFailure = (failure) ->
                  console.error(failure.message)

                cookbookresource.save().then(onSuccess, onFailure)
              )
            )
          else
            controller.set('resourcesSaved', 0)
            controller.set('resourceSaving', 0)
            controller.set("nameError", true)
        else
          controller.set('resourcesSaved', 0)
          controller.set('resourceSaving', 0)
          controller.set("duplicateError", true)
      )
  }
})

Editor.CookbookresourcesSettingsRoute = Ember.Route.extend({
  model: ->
    Ember.RSVP.hash({
      cookbookresource: this.modelFor('cookbookresources'),
      dfasdlresources: this.store.findAll('dfasdlresource')
    })

  setupController: (controller, model) ->
    controller.set('model', model)
    # Load all source dfasdl versions, their current selection and set them in the controller.
    promises = model.cookbookresource.get('cookbook').sources.map (dfasdl) ->
      EditorHelpers.loadDfasdlVersions(dfasdl, model.dfasdlresources)
    Ember.RSVP.all(promises).then((results) -> controller.set('sourcesVersions', results))
    target = model.cookbookresource.get('cookbook').target
    if (target)
      targetPromise = EditorHelpers.loadDfasdlVersions(target, model.dfasdlresources)
      targetPromise.then((result) -> controller.set('targetVersions', result))
    else
      controller.set('targetVersions', null)
    controller.set('settingsSaved', 0)
    controller.set('settingsSaving', 0)

  actions: {
    saveSettings: ->
      controller = this.controllerFor('cookbookresourcesSettings')
      controller.set('settingsSaving', 1)
      cookbookresource = this.currentModel.cookbookresource
      dfasdlresources = this.currentModel.dfasdlresources
      cookbook = cookbookresource.get('cookbook')
      promises = []
      # Get the selected target version from the controller and load it from the server if neccessary.
      selectedTargetVersion = controller.get('targetVersions').selected
      resource = EditorHelpers.findDfasdlResource(cookbook.target.id, dfasdlresources)
      target = EditorHelpers.loadDfasdl(resource.id, selectedTargetVersion)
      target.then((result) -> cookbook.target = result).then(->
        # Loop through the sources from the controller and prepare them for loading if neccessary.
        sourceIdVersions = {}
        cookbook.sources.forEach (source) -> sourceIdVersions[source.id] = source.version
        promises = controller.get('sourcesVersions').map (source) ->
          resource = EditorHelpers.findDfasdlResource(source.id, dfasdlresources)
          EditorHelpers.loadDfasdl(resource.id, source.selected)
        Ember.RSVP.all(promises).then((results) -> cookbook.sources = results).then(->
          cookbookresource.save()
          controller.set('settingsSaved', 1)
          controller.set('settingsSaving', 0)
        )
      )
      console.log('Settings saved.')
  }
})

