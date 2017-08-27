$ ->
  window.ConInfoFormHelpers = {
    selectedDfasdlId: '',
    createDfasdlOptions: (ids) ->
      $('select#dfasdlId').empty()
      ids.forEach((id) ->
        e = document.createElement("option")
        e.innerHTML = id
        e.value = id
        if (ConInfoFormHelpers.selectedDfasdlId.length > 0 && id == ConInfoFormHelpers.selectedDfasdlId)
          e.selected = 'selected'
        $('select#dfasdlId').append(e)
      )
      $('select#dfasdlId').prop('disable', false)

    cookbookChanged: (id) ->
      if (id == undefined || id.length == 0)
        ConInfoFormHelpers.createDfasdlOptions([])
      else
        $.ajax '/cookbookresources/'+id+'/dfasdls',
          type: 'GET'
          dataType: 'json'
          error: (jqXHR, textStatus, errorThrown) ->
            $('select#dfasdlId').empty()
          success: (data, textStatus, jqXHR) ->
            ConInfoFormHelpers.createDfasdlOptions(data)
  }

  $('select#cookbookId').on('change', ->
    ConInfoFormHelpers.selectedDfasdlId = ''
    $('select#dfasdlId').prop('disable', true)
    ConInfoFormHelpers.cookbookChanged(this.value)
  )

  ConInfoFormHelpers.cookbookChanged($('select#cookbookId').val())

  addAutocomplete = (element) ->
    suggesterUrl = $(element).data('url')
    $(element).autocomplete
      minLength: 1
      source: (request, response) ->
        $.get(suggesterUrl, request, response)

  addAutocomplete element for element in $('input.autocomplete')
