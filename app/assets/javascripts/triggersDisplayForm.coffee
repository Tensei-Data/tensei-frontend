$ ->
  $('#source_selector_camel').on('click', ->
    if($('#source_selector_camel').is(':checked'))
      $('#triggerTkId').val('')
      $('#trigger_transformation_configuration_wrapper').hide()
      $('#endpoint_uri_wrapper').show()
  )

  $('#source_selector_tc').on('click', ->
    if($('#source_selector_tc').is(':checked'))
      $('#endpointUri').val('')
      $('#endpoint_uri_wrapper').hide()
      $('#trigger_transformation_configuration_wrapper').show()
  )

  if($('#triggerTkId').val() == null || $('#triggerTkId').val() == '')
    $('#trigger_transformation_configuration_wrapper').hide()
    $('#source_selector_camel').prop('checked', true)
  else
    $('#endpoint_uri_wrapper').hide()
    $('#source_selector_tc').prop('checked', true)
