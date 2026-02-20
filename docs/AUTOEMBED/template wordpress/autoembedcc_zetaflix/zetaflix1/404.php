<?php
/*
* -------------------------------------------------------------------------------------
* @author: Zetathemes
* @author URI: https://zetathemes.com/
* @copyright: (c) 2023 Zetathemes. All rights reserved
* -------------------------------------------------------------------------------------
*
* @since 1.0.0
*
*/
get_header(); ?>
<main>
	<div class="display-page">
		<div class="page-body">
			<div class="not-found">
				<h3><?php _z('ERROR'); ?> 404</h3>
				<p class="no-result"><?php _z('Page not found'); ?></p>
				<div class="clearfix"></div>
			</div>
		</div>
	</div>
</main>
<?php get_template_part('inc/parts/sidebar');?>


<?php get_footer(); ?>
