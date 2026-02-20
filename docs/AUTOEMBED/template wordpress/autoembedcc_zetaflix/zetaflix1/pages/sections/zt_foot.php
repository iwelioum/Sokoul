<?php

$json = array(
    'url'       => admin_url('admin-ajax.php', 'relative'),
    'wait'      => __z('please wait...'),
    'error'     => __z('Unknown error'),
    'loading'   => __z('Loading...'),
    'recaptcha' => zeta_get_option('gcaptchasitekeyv3')
);

$json = json_encode( $json );

?>    
</div>
<div class="text_ft"><?php bloginfo('name'); ?> &copy; <?php echo date('Y'); ?></div>
	
	</div>
</div>
<div class="body-bg"><img src="<?php echo ZETA_URI.'/assets/img/bg.png'; ?>"></div>
<script type='text/javascript'>
    var Auth = <?php echo $json; ?>;
</script>
<script type='text/javascript' src='<?php echo ZETA_URI.'/assets/js/front.auth'.zeta_devmode().'.js'; ?>'></script>
</body>
</html>
