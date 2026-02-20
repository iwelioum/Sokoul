<?php
/**
 * Displays controls
 *
 * @package fmovie
 */

?>

<div id="controls">
	<div class="items">
		<?php echo TrailerButton(); ?>
		<div class="ctl light d-none d-md-block btn bp-btn-light"><i class="fa fa-adjust"></i>  <?php echo streaming; ?></div>
		<div data-go="#comment"><i class="fa fa-comment"></i> <?php echo txtcomments ?></div>
		<div class="ctl d-none d-md-block views"><i class="fa fa-eye"></i> <?php echo getPostViews(get_the_ID()); ?><?php echo mostwatched; ?></div>
		<div class="ctl report" data-toggle="modal" data-target="#report-video"><i class="fa fa-exclamation-circle"></i>  <?php echo esc_html__( 'Report', 'fmovie' ) ?></div>
		<?php echo Favorite(); ?>
	</div>
	<div class="clearfix"></div>
</div>