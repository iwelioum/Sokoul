<div>
	<meta itemprop="name" content="<?php echo esc_attr( starstruck_get_microdata_name() ); ?>">
	<?php do_action('starstruck_microdata'); ?>
	<div itemscope class="starstruck-wrap" itemprop="aggregateRating" itemtype="http://schema.org/AggregateRating">
		<meta itemprop="bestRating" content="10"/>
		<meta itemprop="worstRating" content="1"/>
		<div class="zt_rating_data">
		
		
		
			
			
          <div class="ratings">
			  <div class="ratings-hint">
				<span class="rate1" data-tip="<?php _z("Bored");?>"></span>
				<span class="rate2" data-tip="<?php _z("Fine");?>"></span>
				<span class="rate3" data-tip="<?php _z("Good");?>"></span>
				<span class="rate4" data-tip="<?php _z("Amazing");?>"></span>
				<span class="rate5" data-tip="<?php _z("Excellent");?>"></span>
			  </div>
            <div class="rating-average"><span class="zt_rating_vgs" itemprop="ratingValue"><?php echo $rating; ?></span></div>
           <?php echo starstruck_return_content_span( $id, $rating, $type ); ?>
            <div class="rating-data">
              <div class="data-total">
                <span class="total-txt" itemprop="ratingCount"><?php echo number_format( $votes ); ?> <?php echo ($votes > 1) ? _z("votes") : _z("vote");?></span>
              </div>
              <div class="data-hover"><span>Awesome!</span></div>
            </div>
          </div>			
			

		</div>
	</div>
</div>
