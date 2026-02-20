<?php
/**
 * @since 3.4.0
 * @version 2.0
 */
CSF::createSection(ZETA_OPTIONS,
    array(
        'title'  => __z('TV Shows > Seasons'),
        'parent' => 'homepage',
        'icon'   => 'fa fa-minus',
        'fields' => array(
            array(
                'id'      => 'seasonstitle',
                'type'    => 'text',
                'title'   => __z('Module Title'),
                'default' => __z('Seasons'),
                'subtitle'    => __z('Add title to show')
            ),
            array(
                'id'    => 'seasonsmodcontrol',
                'type'  => 'checkbox',
                'title' => __z('Module Control'),
                'subtitle'  => __z('Check to enable option.'),
                'options' => array(
                    'slider' => __z('Activate Module'),
                ),
                'default'=> array('slider')
            ),
            array(
                'id'    => 'seasonsmdisplay',
                'type'  => 'radio',
                'title' => __z('Display As'),
                'subtitle' => __z('Select style of this modulee'),
                'options' => array(
                    'slider' => __z('Slider'),
                    'grid'  => __z('Grid')
                ),
                'default' => 'slider'
            ),			
            array(
                'id'      => 'seasonsitems',
                'type'    => 'text',
                'title'   => __z('Slide Items'),
                'subtitle' => __z('Number of items to show'),
                'default' => '18',
                'attributes' => array(
                    'type' => 'number',
                    'style' => 'width:100px'
                ),
                'dependency' => array('seasonsmdisplay','==','slider'),
            ),
            array(
                'id'      => 'seasonsgitems',
                'type'    => 'text',
                'title'   => __z('Grid Items'),
                'subtitle' => __z('Number of items to show'),
                'default' => '18',
                'attributes' => array(
                    'type' => 'number',
                    'style' => 'width:100px'
                ),
                'dependency' => array('seasonsmdisplay','==','grid'),
			),
            array(
                'id'      => 'seasonsmodorderby',
                'type'    => 'select',
                'title'   => __z('Order by'),
                'subtitle' => __z('Order items for this module'),
                'default' => 'date',
                'options' => array(
                    'date'     => __z('Post date'),
                    'title'    => __z('Post title'),
                    'modified' => __z('Last modified'),
                    'rand'     => __z('Random entry')
                )
            ),
            array(
                'id'    => 'seasonsmodorder',
                'type'  => 'radio',
                'title' => __z('Order'),
                'options' => array(
                    'DESC' => __z('Descending'),
                    'ASC'  => __z('Ascending')
                ),
                'dependency' => array('seasonsmodorderby','!=','rand'),
                'default' => 'DESC'
            ),
            array(
                'id'    => 'seasonssource',
                'type'  => 'radio',
                'title' => __z('Poster Source'),
                'options' => array(
                    'inherit'    => __z('Inherit from TV Show'),
                    'default' => __z('Default post image'),
                ),
                'default' => 'inherit',
            )
        )
    )
);
