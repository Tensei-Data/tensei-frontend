$ ->
  $("*[data-confirm]").each ->
    message = $(this).attr('data-confirm')

    $(this).click(->
      confirm(message)
    )