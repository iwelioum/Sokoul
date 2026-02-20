		jQuery(function($) {

			$('body').on('click', '.wc_multi_upload_image_button', function(e) {
				e.preventDefault();
				var field = $(this).data('id');
				var fieldName = '#' + field;
				var button = $(this),
				custom_uploader = wp.media({
					title: 'Insert image',
					button: { text: 'Use this image' },
					multiple: true 
				}).on('select', function() {
					var attech_ids = '';
					var attech_urls = '';
					var attachment_urls = '';
					attachments
					var attachments = custom_uploader.state().get('selection'),
					attachment_ids = new Array(),
					//attachment_urls = new Array(),
					i = 0;
					attachments.each(function(attachment) {
						attachment_ids[i] = attachment['id'];
						attech_ids += '\n' + attachment['id'];
						
						attachment_urls += attachment.attributes.url + '\n';
						attech_urls += '\n' + attachment.attributes.url;
						
						if (attachment.attributes.type == 'image') {
							$(button).siblings('ul').append('<li data-attechment-id="' + attachment['id'] + '"><a href="' + attachment.attributes.url + '" target="_blank"><img class="true_pre_image" src="' + attachment.attributes.url + '" /></a><i data-field-id="' + field + '" class=" dashicons dashicons-no delete-img"></i></li>');
						} else {
							$(button).siblings('ul').append('<li data-attechment-id="' + attachment['id'] + '"><a href="' + attachment.attributes.url + '" target="_blank"><img class="true_pre_image" src="' + attachment.attributes.icon + '" /></a><i data-field-id="' + field + '" class=" dashicons dashicons-no delete-img"></i></li>');
						}

						i++;
					});

					var urls = $(fieldName).val();
					if (urls) {
						var urls = urls + attech_urls;
						$(fieldName).val(urls);
					} else {
						$(fieldName).val(attachment_urls);
					}
					$(button).siblings('.wc_multi_remove_image_button').show();
				})
				.open();
			});

			$('body').on('click', '.wc_multi_remove_image_button', function() {
				var field = $(this).data('id');
				var fieldName = '#' + field;
				$(fieldName).val('');
				$(this).hide().prev().val('').prev().addClass('button').html('Add Media');
				$(this).parent().find('ul').empty();
				return false;
			});

		});

	
			jQuery(document).on('click', '.multi-upload-medias ul li i.delete-img', function() {

				var ids = [];
				var field = jQuery(this).data('field-id');
				var fieldName = '#' + field;
				
				
				var url = jQuery(this).closest('li').children('a').children('img').attr('src');
		
				var this_c = jQuery(this);
				jQuery(this).parent().remove();
				jQuery('.multi-upload-medias ul li').each(function() {
					ids.push(jQuery(this).attr('data-attechment-id'));
				});
				

				
				var text = jQuery(fieldName).val();
				console.log(text);
				jQuery(this).val(text.replace('', '')); 
				

				
			});
	