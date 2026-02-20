jQuery(function($){
    $('body').on('click', '.zt_upload_image_button', function(e){
        e.preventDefault();
		var field = $(this).data('id');
        zt_uploader = wp.media({
            title: 'Logo Image',
            button: {
                text: 'Use this image'
            },
            multiple: false
        }).on('select', function() {
            var attachment = zt_uploader.state().get('selection').first().toJSON();
			var fieldName = '#' + field;			
            $(fieldName).val(attachment.url);
        })
        .open();
    });
});